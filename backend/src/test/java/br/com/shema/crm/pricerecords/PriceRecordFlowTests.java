package br.com.shema.crm.pricerecords;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.shema.crm.TestcontainersConfiguration;
import br.com.shema.crm.identity.domain.Role;
import br.com.shema.crm.identity.domain.User;
import br.com.shema.crm.identity.infrastructure.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** Documentos, ata e caronas pela API, contra Postgres real e armazenamento em disco. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "shema.bootstrap-admin.email=admin@test.local",
            "shema.bootstrap-admin.password=Senha-forte-123",
            "shema.auth.login-attempts-per-minute=1000"
        })
@SuppressWarnings({"unchecked", "rawtypes"})
class PriceRecordFlowTests {

    /** Licitações do seed: Conceição de Macabu (vencida, sem ata) e Nova Iguaçu (em disputa). */
    private static final String MACABU_TENDER = "20000000-0000-4000-8000-000000000009";

    private static final String DISPUTE_TENDER = "20000000-0000-4000-8000-000000000001";
    private static final String MACABU = "10000000-0000-4000-8000-000000000007";
    private static final String NOVA_IGUACU = "10000000-0000-4000-8000-000000000017";
    private static final String PETROPOLIS = "10000000-0000-4000-8000-000000000018";
    private static final byte[] PDF_V1 = "%PDF-1.7 ata v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PDF_V2 = "%PDF-1.7 ata v2 assinada".getBytes(StandardCharsets.US_ASCII);

    @LocalServerPort
    int port;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {})
                .build();
    }

    private String token(String email, String password) {
        return (String) http.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .retrieve()
                .toEntity(Map.class)
                .getBody()
                .get("accessToken");
    }

    private String userToken(String email, Role role) {
        if (users.findByEmail(email).isEmpty()) {
            users.save(new User("Teste", email, passwordEncoder.encode("Senha-teste-123"), role));
        }
        return token(email, "Senha-teste-123");
    }

    private ResponseEntity<Map> send(HttpMethod method, String token, String uri, Object body) {
        var request = http.method(method).uri(uri).header("Authorization", "Bearer " + token);
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return request.retrieve().toEntity(Map.class);
    }

    private ResponseEntity<Map> upload(String token, String uri, String fileName, byte[] content) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        return http.post()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .toEntity(Map.class);
    }

    private Map ok(ResponseEntity<Map> response, int status) {
        assertThat(response.getStatusCode().value())
                .as("%s", response.getBody())
                .isEqualTo(status);
        return response.getBody();
    }

    private static Map line(String itemId, String quantity) {
        return Map.of("itemId", itemId, "quantity", quantity);
    }

    @Test
    void documentAtaAndCaronasEndToEnd() {
        String admin = token("admin@test.local", "Senha-forte-123");

        // documento com duas versões; a primeira continua baixável
        String docUri =
                "/api/v1/documents?tenderId=" + MACABU_TENDER + "&kind=ATA&title=Ata 022-2026&documentDate=2026-10-25";
        ResponseEntity<Map> refused = upload(admin, docUri, "ata.html", PDF_V1);
        assertThat(refused.getStatusCode().value()).isEqualTo(422);
        assertThat(refused.getBody()).containsEntry("rule", "FILE_TYPE_NOT_ALLOWED");
        Map doc = ok(upload(admin, docUri, "C:\\temp\\Ata 022-2026.pdf", PDF_V1), 201);
        String docId = (String) doc.get("id");
        assertThat(doc.get("kindLabel")).isEqualTo("Ata");
        assertThat(((List<Map>) doc.get("versions")).getFirst().get("fileName")).isEqualTo("Ata 022-2026.pdf");

        doc = ok(
                upload(
                        admin,
                        "/api/v1/documents/" + docId + "/versions?version=" + doc.get("version"),
                        "ata-assinada.pdf",
                        PDF_V2),
                200);
        assertThat(doc.get("currentVersion")).isEqualTo(2);
        assertThat((List<Map>) doc.get("versions"))
                .extracting(v -> v.get("number"))
                .containsExactly(2, 1);

        ResponseEntity<byte[]> v1 = http.get()
                .uri("/api/v1/documents/" + docId + "/versions/1/file")
                .header("Authorization", "Bearer " + admin)
                .retrieve()
                .toEntity(byte[].class);
        assertThat(v1.getBody()).isEqualTo(PDF_V1);
        assertThat(v1.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(v1.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment");

        // ata só para licitação vencida, uma por licitação
        Map record = Map.of(
                "number",
                "ARP 022/2026",
                "signedAt",
                "2026-10-25",
                "validUntil",
                LocalDate.now().plusYears(1).toString(),
                "allowsAdhesion",
                true,
                "documentId",
                docId);
        assertThat(ok(
                        send(
                                HttpMethod.POST,
                                admin,
                                "/api/v1/price-records",
                                Map.of("tenderId", DISPUTE_TENDER, "record", record)),
                        422))
                .containsEntry("rule", "TENDER_NOT_WON");
        Map ata = ok(
                send(
                        HttpMethod.POST,
                        admin,
                        "/api/v1/price-records",
                        Map.of("tenderId", MACABU_TENDER, "record", record)),
                201);
        String ataId = (String) ata.get("id");
        assertThat(ata.get("situation")).isEqualTo("VIGENTE");
        assertThat(ata.get("opportunity")).isEqualTo(true);
        assertThat(((Map) ata.get("document")).get("currentVersion")).isEqualTo(2);
        ok(
                send(
                        HttpMethod.POST,
                        admin,
                        "/api/v1/price-records",
                        Map.of("tenderId", MACABU_TENDER, "record", record)),
                409);

        ata = ok(
                send(
                        HttpMethod.PUT,
                        admin,
                        "/api/v1/price-records/" + ataId + "/items",
                        Map.of(
                                "version",
                                ata.get("version"),
                                "items",
                                List.of(Map.of(
                                        "description", "Kit escolar fundamental",
                                        "unit", "kit",
                                        "quantity", "100",
                                        "unitPrice", "42.60")))),
                200);
        String kit = (String) ((List<Map>) ata.get("items")).getFirst().get("id");
        assertThat(new BigDecimal(ata.get("referenceValue").toString())).isEqualByComparingTo("4260.00");

        // municípios sem ata, antes das caronas
        List<Map> prospects = http.get()
                .uri("/api/v1/price-records/prospects?state=RJ")
                .header("Authorization", "Bearer " + admin)
                .retrieve()
                .body(List.class);
        assertThat(prospects)
                .extracting(p -> p.get("id"))
                .contains(NOVA_IGUACU, PETROPOLIS)
                .doesNotContain(MACABU);

        // carona dentro do limite: valor calculado pelos itens
        Map first = Map.of("agencyId", NOVA_IGUACU, "adheredAt", "2026-10-28", "lines", List.of(line(kit, "150")));
        Map check = ok(send(HttpMethod.POST, admin, "/api/v1/price-records/" + ataId + "/caronas/check", first), 200);
        assertThat(new BigDecimal(check.get("value").toString())).isEqualByComparingTo("6390.00");
        assertThat((List) check.get("warnings")).isEmpty();
        ata = ok(
                send(
                        HttpMethod.POST,
                        admin,
                        "/api/v1/price-records/" + ataId + "/caronas",
                        Map.of("carona", first, "confirmLimits", false)),
                201);
        assertThat(ata.get("agencyCount")).isEqualTo(1);

        // passa de 200% do item: avisa, recusa sem confirmação, registra marcada com confirmação
        Map second = Map.of("agencyId", PETROPOLIS, "adheredAt", "2026-10-29", "lines", List.of(line(kit, "100")));
        check = ok(send(HttpMethod.POST, admin, "/api/v1/price-records/" + ataId + "/caronas/check", second), 200);
        assertThat((List<Map>) check.get("warnings"))
                .extracting(w -> w.get("code"))
                .containsExactly("ITEM_LIMIT");
        assertThat(ok(
                        send(
                                HttpMethod.POST,
                                admin,
                                "/api/v1/price-records/" + ataId + "/caronas",
                                Map.of("carona", second, "confirmLimits", false)),
                        422))
                .containsEntry("rule", "CARONA_LIMITS");
        ata = ok(
                send(
                        HttpMethod.POST,
                        admin,
                        "/api/v1/price-records/" + ataId + "/caronas",
                        Map.of("carona", second, "confirmLimits", true)),
                201);
        List<Map> caronas = (List<Map>) ata.get("caronas");
        assertThat(caronas).hasSize(2);
        assertThat(caronas.get(1).get("limitsOverridden")).isEqualTo(true);
        assertThat(((Map) caronas.get(1).get("agency")).get("name")).isEqualTo("Prefeitura Municipal de Petrópolis");
        assertThat(((List<Map>) ata.get("items")).getFirst().get("used").toString())
                .startsWith("250");

        // o órgão dono da ata não pega carona nela
        assertThat(ok(
                        send(
                                HttpMethod.POST,
                                admin,
                                "/api/v1/price-records/" + ataId + "/caronas/check",
                                Map.of(
                                        "agencyId",
                                        MACABU,
                                        "adheredAt",
                                        "2026-10-29",
                                        "lines",
                                        List.of(line(kit, "1")))),
                        422))
                .containsEntry("rule", "OWN_AGENCY");

        // destaques e lista
        List<Map> highlights = http.get()
                .uri("/api/v1/price-records/highlights?tenderIds=" + MACABU_TENDER + "," + DISPUTE_TENDER)
                .header("Authorization", "Bearer " + admin)
                .retrieve()
                .body(List.class);
        assertThat(highlights)
                .singleElement()
                .satisfies(h -> assertThat(h.get("opportunity")).isEqualTo(true));
        List<Map> all = http.get()
                .uri("/api/v1/price-records?filter=TODAS")
                .header("Authorization", "Bearer " + admin)
                .retrieve()
                .body(List.class);
        assertThat(all).extracting(a -> a.get("number")).contains("ARP 022/2026", "ARP 014/2025", "ARP 033/2026");

        // apagar carona e ata com carona
        ok(
                send(
                        HttpMethod.DELETE,
                        admin,
                        "/api/v1/price-records/" + ataId + "?version=" + ata.get("version"),
                        null),
                422);
        ata = ok(
                send(
                        HttpMethod.DELETE,
                        admin,
                        "/api/v1/price-records/" + ataId + "/caronas/"
                                + caronas.get(1).get("id") + "?version=" + ata.get("version"),
                        null),
                200);
        assertThat(ata.get("agencyCount")).isEqualTo(1);

        // apagar documento é lógico: some da lista e do download
        ok(send(HttpMethod.DELETE, admin, "/api/v1/documents/" + docId + "?version=" + doc.get("version"), null), 204);
        List<Map> docs = http.get()
                .uri("/api/v1/documents?tenderId=" + MACABU_TENDER)
                .header("Authorization", "Bearer " + admin)
                .retrieve()
                .body(List.class);
        assertThat(docs).isEmpty();
        assertThat(http.get()
                        .uri("/api/v1/documents/" + docId + "/versions/1/file")
                        .header("Authorization", "Bearer " + admin)
                        .retrieve()
                        .toEntity(byte[].class)
                        .getStatusCode()
                        .value())
                .isEqualTo(404);

        List<Map> history = http.get()
                .uri("/api/v1/price-records/history?tenderId=" + MACABU_TENDER)
                .header("Authorization", "Bearer " + admin)
                .retrieve()
                .body(List.class);
        assertThat(history)
                .extracting(e -> e.get("type"))
                .contains(
                        "DOCUMENT_ADDED",
                        "DOCUMENT_VERSION_ADDED",
                        "ATA_CREATED",
                        "ITEMS_CHANGED",
                        "CARONA_REGISTERED",
                        "CARONA_REMOVED",
                        "DOCUMENT_DELETED");
        assertThat(history.stream().filter(e -> "CARONA_REGISTERED".equals(e.get("type"))))
                .anySatisfy(e -> assertThat((String) e.get("summary")).contains("confirmada"));
    }

    @Test
    void onlyTheOwnerOrAdminChangesAndViewersOnlyRead() {
        String sales = userToken("vendedor.atas@test.local", Role.SALES);
        String viewer = userToken("leitor.atas@test.local", Role.VIEWER);
        String sefazTender = "20000000-0000-4000-8000-000000000013";

        // licitação de outro responsável (Edwin)
        assertThat(upload(sales, "/api/v1/documents?tenderId=" + sefazTender + "&kind=OUTROS&title=X", "x.pdf", PDF_V1)
                        .getStatusCode()
                        .value())
                .isEqualTo(403);
        assertThat(upload(viewer, "/api/v1/documents?tenderId=" + sefazTender + "&kind=OUTROS&title=X", "x.pdf", PDF_V1)
                        .getStatusCode()
                        .value())
                .isEqualTo(403);

        Map ata = ok(send(HttpMethod.GET, viewer, "/api/v1/price-records/by-tender/" + sefazTender, null), 200);
        assertThat(ata.get("canEdit")).isEqualTo(false);
        assertThat(ata.get("number")).isEqualTo("ARP 017/2026");
        assertThat(((List) ata.get("caronas"))).hasSize(1);
        assertThat(send(
                                HttpMethod.POST,
                                sales,
                                "/api/v1/price-records/" + ata.get("id") + "/caronas",
                                Map.of(
                                        "carona",
                                        Map.of("agencyId", NOVA_IGUACU, "adheredAt", "2026-10-29", "value", "10"),
                                        "confirmLimits",
                                        true))
                        .getStatusCode()
                        .value())
                .isEqualTo(403);
    }
}
