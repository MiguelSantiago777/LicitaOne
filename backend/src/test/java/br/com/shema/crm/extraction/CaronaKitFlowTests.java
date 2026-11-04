package br.com.shema.crm.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.shema.crm.TestcontainersConfiguration;
import br.com.shema.crm.extraction.application.DocumentReader;
import br.com.shema.crm.extraction.domain.RawCaronaKit;
import br.com.shema.crm.identity.domain.Role;
import br.com.shema.crm.identity.domain.User;
import br.com.shema.crm.identity.infrastructure.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

/**
 * Kit de carona (Fase 10), com banco real e um leitor falso: quanto cada município pode pedir (50% por órgão, sem
 * passar do total), o kit sem IA e com IA, o .docx e quem pode pedir.
 */
@Import({TestcontainersConfiguration.class, CaronaKitFlowTests.FakeReaderConfig.class})
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "shema.bootstrap-admin.email=admin@test.local",
            "shema.bootstrap-admin.password=Senha-forte-123",
            "shema.auth.login-attempts-per-minute=1000",
            "shema.company.cnpj=12.345.678/0001-90",
            "shema.company.address=Rua das Flores, 10, Niterói/RJ",
            "shema.company.city=Niterói/RJ",
            "shema.company.representative=Maria Souza",
            "shema.company.representative-role=Sócia-administradora"
        })
@SuppressWarnings({"unchecked", "rawtypes"})
class CaronaKitFlowTests {

    static final AtomicReference<DocumentReader.CaronaKitRequest> LAST = new AtomicReference<>();

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeReaderConfig {

        @Bean
        @Primary
        DocumentReader fakeWriter() {
            return new DocumentReader() {
                @Override
                public boolean enabled() {
                    return true;
                }

                @Override
                public Reading read(ReadRequest request) {
                    throw new ReadingFailed("não usado aqui");
                }

                @Override
                public Answer<RawCaronaKit> writeCaronaKit(CaronaKitRequest request) {
                    LAST.set(request);
                    return new Answer<>(
                            new RawCaronaKit(
                                    "Resmas pela ata de Magé",
                                    "Prezados(as), oferecemos a adesão à ata...",
                                    "O município precisa de papel para as escolas.",
                                    List.of()),
                            "leitor-falso",
                            700,
                            300);
                }
            };
        }
    }

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

    private String admin() {
        return token("admin@test.local", "Senha-forte-123");
    }

    private String viewer() {
        String email = "leitura-carona@test.local";
        if (users.findByEmail(email).isEmpty()) {
            users.save(new User("Leitura", email, passwordEncoder.encode("Senha-teste-123"), Role.VIEWER));
        }
        return token(email, "Senha-teste-123");
    }

    private ResponseEntity<Map> send(HttpMethod method, String token, String uri, Object body) {
        RestClient.RequestBodySpec request = http.method(method).uri(uri).header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return request.retrieve().toEntity(Map.class);
    }

    private static Map ok(ResponseEntity<Map> response, int status) {
        assertThat(response.getStatusCode().value())
                .as("%s", response.getBody())
                .isEqualTo(status);
        return response.getBody();
    }

    private String agency(String token, String name, String city) {
        return (String) ok(
                        send(
                                HttpMethod.POST,
                                token,
                                "/api/v1/agencies",
                                Map.of(
                                        "name",
                                        name + " " + UUID.randomUUID(),
                                        "sphere",
                                        "MUNICIPAL",
                                        "city",
                                        city,
                                        "state",
                                        "RJ")),
                        201)
                .get("id");
    }

    @Test
    void offersHalfOfEachItemPerMunicipalityAndWritesTheKit() {
        String admin = admin();
        String mage = agency(admin, "Prefeitura de Magé", "Magé");
        String tenderId = (String) ok(
                        send(
                                HttpMethod.POST,
                                admin,
                                "/api/v1/tenders",
                                Map.of(
                                        "initialStatus",
                                        "VENCIDA",
                                        "wonAt",
                                        "2026-04-10",
                                        "tender",
                                        Map.of(
                                                "agencyId", mage,
                                                "processNumber", "12/2026",
                                                "object", "Material de expediente",
                                                "materialType", "Expediente",
                                                "totalValue", 300000.00))),
                        201)
                .get("id");
        Map ata = ok(
                send(
                        HttpMethod.POST,
                        admin,
                        "/api/v1/price-records",
                        Map.of(
                                "tenderId",
                                tenderId,
                                "record",
                                Map.of(
                                        "number",
                                        "ARP 031/2026",
                                        "validUntil",
                                        LocalDate.now().plusMonths(6).toString(),
                                        "allowsAdhesion",
                                        true))),
                201);
        String ataId = (String) ata.get("id");
        assertThat(((Map) ata.get("limits")).get("maxPerAgencyPercent")).isEqualTo(50);
        ata = ok(
                send(
                        HttpMethod.PUT,
                        admin,
                        "/api/v1/price-records/" + ataId + "/items",
                        Map.of(
                                "version",
                                ata.get("version"),
                                "items",
                                List.of(
                                        Map.of(
                                                "description",
                                                "Resma de papel A4",
                                                "unit",
                                                "pct",
                                                "quantity",
                                                "100",
                                                "unitPrice",
                                                "25.00"),
                                        Map.of(
                                                "description",
                                                "Caneta azul",
                                                "unit",
                                                "un",
                                                "quantity",
                                                "1000",
                                                "unitPrice",
                                                "2.00")))),
                200);
        List<Map> items = (List<Map>) ata.get("items");
        String resma = (String) items.get(0).get("id");
        String caneta = (String) items.get(1).get("id");

        // Itaguaí já aderiu a 30 resmas: pode mais 20; Itaboraí, novo, pode 50
        String itaguai = agency(admin, "Prefeitura de Itaguaí", "Itaguaí");
        ok(
                send(
                        HttpMethod.POST,
                        admin,
                        "/api/v1/price-records/" + ataId + "/caronas",
                        Map.of(
                                "carona",
                                Map.of(
                                        "agencyId",
                                        itaguai,
                                        "adheredAt",
                                        LocalDate.now().toString(),
                                        "lines",
                                        List.of(Map.of("itemId", resma, "quantity", "30"))),
                                "confirmLimits",
                                false)),
                201);
        Map again = ok(
                send(
                        HttpMethod.GET,
                        admin,
                        "/api/v1/price-records/" + ataId + "/carona-offer?agencyId=" + itaguai,
                        null),
                200);
        List<Map> againItems = (List<Map>) ((Map) again.get("offer")).get("items");
        assertThat(new BigDecimal(againItems.get(0).get("maxQuantity").toString()))
                .isEqualByComparingTo("20");
        assertThat((List<Map>) ((Map) again.get("offer")).get("warnings"))
                .extracting(w -> w.get("code"))
                .containsExactlyInAnyOrder("ALREADY_ADHERED", "MUNICIPAL_MANAGER");

        String itaborai = agency(admin, "Prefeitura de Itaboraí", "Itaboraí");
        Map offer = ok(
                send(
                        HttpMethod.GET,
                        admin,
                        "/api/v1/price-records/" + ataId + "/carona-offer?agencyId=" + itaborai,
                        null),
                200);
        assertThat(offer).containsEntry("aiEnabled", true);
        assertThat((List) offer.get("companyMissing")).isEmpty();
        List<Map> offerItems = (List<Map>) ((Map) offer.get("offer")).get("items");
        assertThat(offerItems)
                .extracting(i -> new BigDecimal(i.get("maxQuantity").toString()).intValueExact())
                .containsExactly(50, 500);

        // sem IA: modelos fixos com as quantidades escolhidas; acima do máximo, avisa
        Map fixed = ok(
                send(
                        HttpMethod.POST,
                        admin,
                        "/api/v1/price-records/" + ataId + "/carona-kit",
                        Map.of(
                                "agencyId",
                                itaborai,
                                "useAi",
                                false,
                                "lines",
                                List.of(
                                        Map.of("itemId", resma, "quantity", "60"),
                                        Map.of("itemId", caneta, "quantity", "0")))),
                200);
        Map kit = (Map) fixed.get("kit");
        assertThat(kit).containsEntry("writtenByAi", false);
        assertThat(new BigDecimal(kit.get("total").toString())).isEqualByComparingTo("1500.00");
        assertThat((List<String>) kit.get("warnings"))
                .singleElement()
                .asString()
                .contains("Resma de papel A4")
                .contains("passa do que o município pode aderir (50)");
        assertThat((String) ((Map) kit.get("consent")).get("body"))
                .contains("CNPJ sob o nº 12.345.678/0001-90")
                .contains("Resma de papel A4: 60 pct × R$ 25,00 = R$ 1.500,00")
                .doesNotContain("Caneta");
        assertThat(fixed.get("model")).isNull();

        // com IA: a carta e a justificativa vêm do modelo; o contexto leva a ata e o máximo de cada item
        Map written = ok(
                send(
                        HttpMethod.POST,
                        admin,
                        "/api/v1/price-records/" + ataId + "/carona-kit",
                        Map.of("agencyId", itaborai, "useAi", true)),
                200);
        assertThat(written).containsEntry("model", "leitor-falso");
        Map aiKit = (Map) written.get("kit");
        assertThat(((Map) aiKit.get("presentation")).get("subject")).isEqualTo("Resmas pela ata de Magé");
        assertThat((String) ((Map) aiKit.get("request")).get("body")).contains("papel para as escolas");
        assertThat(LAST.get().context())
                .contains("ARP 031/2026")
                .contains("Prefeitura de Itaboraí")
                .contains("Resma de papel A4: 50 pct")
                .contains("Caneta azul: 500 un")
                .contains("até 50% do registrado");

        // .docx da anuência
        ResponseEntity<byte[]> docx = http.post()
                .uri("/api/v1/price-records/" + ataId + "/carona-kit/docx")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("agencyId", itaborai, "kind", "ANUENCIA", "text", ((Map) kit.get("consent")).get("body")))
                .retrieve()
                .toEntity(byte[].class);
        assertThat(docx.getStatusCode().value()).isEqualTo(200);
        assertThat(new String(docx.getBody(), 0, 2)).isEqualTo("PK");
        assertThat(docx.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("031-2026")
                .contains(".docx");

        // o gerenciador não recebe oferta de carona da própria ata; leitura não pede kit
        assertThat(ok(
                        send(
                                HttpMethod.GET,
                                admin,
                                "/api/v1/price-records/" + ataId + "/carona-offer?agencyId=" + mage,
                                null),
                        422))
                .containsEntry("rule", "OWN_AGENCY");
        assertThat(send(
                                HttpMethod.POST,
                                viewer(),
                                "/api/v1/price-records/" + ataId + "/carona-kit",
                                Map.of("agencyId", itaborai, "useAi", false))
                        .getStatusCode()
                        .value())
                .isEqualTo(403);
    }
}
