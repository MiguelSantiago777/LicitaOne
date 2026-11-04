package br.com.shema.crm.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.shema.crm.TestcontainersConfiguration;
import br.com.shema.crm.extraction.application.DocumentReader;
import br.com.shema.crm.extraction.domain.RawOrder;
import br.com.shema.crm.extraction.domain.RawOrder.RawOrderLine;
import br.com.shema.crm.extraction.domain.RawReading.RawField;
import br.com.shema.crm.extraction.infrastructure.OrderReadingRepository;
import br.com.shema.crm.identity.domain.Role;
import br.com.shema.crm.identity.domain.User;
import br.com.shema.crm.identity.infrastructure.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Pedido a partir de documento ou mensagem (Fase 8), com fila e banco reais e um leitor falso: a leitura relaciona as
 * linhas com os itens do contrato, avisa o que passa do saldo, apaga o texto colado ao terminar, e o pedido
 * registrado guarda o documento de origem e que veio da IA.
 */
@Import({TestcontainersConfiguration.class, OrderReadingFlowTests.FakeReaderConfig.class})
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "shema.bootstrap-admin.email=admin@test.local",
            "shema.bootstrap-admin.password=Senha-forte-123",
            "shema.auth.login-attempts-per-minute=1000"
        })
@SuppressWarnings({"unchecked", "rawtypes"})
class OrderReadingFlowTests {

    private static final byte[] PDF = "%PDF-1.7 autorização de fornecimento".getBytes(StandardCharsets.UTF_8);

    /** O que o leitor falso recebeu por último, para conferir o que foi mandado ao modelo. */
    static final AtomicReference<DocumentReader.OrderReadRequest> LAST = new AtomicReference<>();

    /** Pede 200 do item 1 (Banner), 5.000 do item 2 (acima do saldo de 4.000) e um toner que não é do contrato. */
    @TestConfiguration(proxyBeanMethods = false)
    static class FakeReaderConfig {

        @Bean
        @Primary
        DocumentReader fakeOrderReader() {
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
                public OrderRead readOrder(OrderReadRequest request) {
                    LAST.set(request);
                    RawOrder raw = new RawOrder(
                            "AUTORIZACAO_FORNECIMENTO",
                            new RawField("2026-10-20", 1),
                            new RawField("AF 77/2026", 1),
                            new RawField("Prefeitura de Itaguaí", 1),
                            new RawField("", 0),
                            new RawField("", 0),
                            List.of(
                                    new RawOrderLine("1", "Banner", "UN", "200", "100.00", 1, "200 banners"),
                                    new RawOrderLine("2", "Adesivo", "UN", "5000", "", 1, "5.000 adesivos"),
                                    new RawOrderLine("", "Toner 85A", "UN", "3", "120.00", 1, "3 toners")),
                            List.of());
                    return new OrderRead(raw, "leitor-falso", 900, 210);
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

    @Autowired
    OrderReadingRepository readings;

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

    private String userToken(String email, Role role) {
        if (users.findByEmail(email).isEmpty()) {
            users.save(new User("Teste", email, passwordEncoder.encode("Senha-teste-123"), role));
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

    /** Licitação vencida com um contrato de dois itens (Banner 2.000 × R$ 100 e Adesivo 4.000 × R$ 25). */
    private Map contract(String token) {
        String agencyId = (String) send(
                        HttpMethod.POST,
                        token,
                        "/api/v1/agencies",
                        Map.of(
                                "name",
                                "Prefeitura de Itaguaí " + UUID.randomUUID(),
                                "sphere",
                                "MUNICIPAL",
                                "city",
                                "Itaguaí",
                                "state",
                                "RJ"))
                .getBody()
                .get("id");
        String tenderId = (String) send(
                        HttpMethod.POST,
                        token,
                        "/api/v1/tenders",
                        Map.of(
                                "initialStatus",
                                "VENCIDA",
                                "wonAt",
                                "2026-04-10",
                                "tender",
                                Map.of(
                                        "agencyId", agencyId,
                                        "processNumber", "39/2026",
                                        "object", "Material gráfico",
                                        "materialType", "Material gráfico",
                                        "totalValue", 300000.00)))
                .getBody()
                .get("id");
        ResponseEntity<Map> signed = send(
                HttpMethod.POST,
                token,
                "/api/v1/tenders/" + tenderId + "/contracts",
                Map.of(
                        "contract",
                        Map.of("kind", "CONTRATO", "number", "12/2026", "signedAt", "2026-05-16", "value", 300000.00),
                        "items",
                        List.of(
                                Map.of("description", "Banner", "unit", "un", "quantity", 2000, "unitPrice", 100),
                                Map.of("description", "Adesivo perfurado", "quantity", 4000, "unitPrice", 25)),
                        "suggestedByAi",
                        true));
        assertThat(signed.getStatusCode().value()).as("%s", signed.getBody()).isEqualTo(201);
        return signed.getBody();
    }

    private String document(String token, String tenderId, String kind, String fileName, byte[] content) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        ResponseEntity<Map> response = http.post()
                .uri("/api/v1/documents?tenderId=" + tenderId + "&kind=" + kind + "&title=" + fileName)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .toEntity(Map.class);
        assertThat(response.getStatusCode().value())
                .as("%s", response.getBody())
                .isEqualTo(201);
        return (String) response.getBody().get("id");
    }

    private Map waitUntilFinished(String token, String readingId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        Map reading;
        do {
            reading = send(HttpMethod.GET, token, "/api/v1/order-readings/" + readingId, null)
                    .getBody();
        } while (!List.of("DONE", "FAILED").contains(reading.get("status"))
                && Instant.now().isBefore(deadline));
        return reading;
    }

    private List<Map> history(String token, String tenderId) {
        return http.get()
                .uri("/api/v1/tenders/" + tenderId + "/contracts/history")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(List.class);
    }

    @Test
    void readsAnOrderDocumentAgainstTheContractAndRecordsWhereTheOrderCameFrom() {
        String admin = admin();
        Map contract = contract(admin);
        String contractId = (String) contract.get("id");
        String tenderId = (String) contract.get("tenderId");
        List<Map> items = (List<Map>) contract.get("items");
        String docId = document(admin, tenderId, "EMPENHO", "af-77-2026.pdf", PDF);

        ResponseEntity<Map> requested = send(
                HttpMethod.POST,
                admin,
                "/api/v1/contracts/" + contractId + "/order-readings",
                Map.of("documentId", docId));
        assertThat(requested.getStatusCode().value())
                .as("%s", requested.getBody())
                .isEqualTo(202);
        assertThat(requested.getBody()).containsEntry("status", "QUEUED").containsEntry("source", "DOCUMENT");

        Map done = waitUntilFinished(admin, (String) requested.getBody().get("id"));
        assertThat(done).containsEntry("status", "DONE").containsEntry("model", "leitor-falso");
        assertThat(LAST.get().contentType()).isEqualTo("application/pdf");
        assertThat(LAST.get().contract()).contains("1 | - | Banner | un | 100").contains("Adesivo perfurado");

        Map result = (Map) done.get("result");
        assertThat((Map) result.get("reference")).containsEntry("value", "AF 77/2026");
        assertThat((List) result.get("warnings")).isEmpty();
        List<Map> lines = (List<Map>) result.get("lines");
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).containsEntry("itemId", items.get(0).get("id"));
        assertThat((List) lines.get(0).get("issues")).isEmpty();
        assertThat((List<String>) lines.get(1).get("issues"))
                .singleElement()
                .asString()
                .contains("saldo é de 4.000");
        assertThat(lines.get(2)).containsEntry("itemId", null);

        // a pessoa confere e registra pelo formulário de sempre, sem a linha que não é do contrato
        ResponseEntity<Map> ordered = send(
                HttpMethod.POST,
                admin,
                "/api/v1/contracts/" + contractId + "/orders",
                Map.of(
                        "version",
                        contract.get("version"),
                        "orderedAt",
                        "2026-10-20",
                        "reference",
                        "AF 77/2026",
                        "lines",
                        List.of(
                                Map.of("itemId", items.get(0).get("id"), "quantity", 200),
                                Map.of("itemId", items.get(1).get("id"), "quantity", 4000)),
                        "sourceDocumentId",
                        docId,
                        "suggestedByAi",
                        true));
        assertThat(ordered.getStatusCode().value()).as("%s", ordered.getBody()).isEqualTo(201);
        List<Map> orders = (List<Map>) ordered.getBody().get("orders");
        assertThat(orders.getFirst()).containsEntry("sourceDocumentId", docId);

        assertThat(history(admin, tenderId))
                .filteredOn(e -> "ORDER_PLACED".equals(e.get("type")))
                .singleElement()
                .satisfies(e -> assertThat((String) e.get("summary"))
                        .contains("AF 77/2026")
                        .contains("documento \"af-77-2026.pdf\"")
                        .endsWith("dados sugeridos pela IA e conferidos por Administrador"));
        assertThat(history(admin, tenderId))
                .filteredOn(e -> "SIGNED".equals(e.get("type")))
                .singleElement()
                .satisfies(e -> assertThat((String) e.get("summary")).contains("sugeridos pela IA"));
    }

    @Test
    void readsAPastedMessageAndDropsTheTextWhenDone() {
        String admin = admin();
        String contractId = (String) contract(admin).get("id");
        String message = "Bom dia! Pedimos 200 banners conforme a AF 77/2026. Att., Maria (21) 99999-0000";

        ResponseEntity<Map> requested = send(
                HttpMethod.POST, admin, "/api/v1/contracts/" + contractId + "/order-readings", Map.of("text", message));
        assertThat(requested.getStatusCode().value())
                .as("%s", requested.getBody())
                .isEqualTo(202);
        String readingId = (String) requested.getBody().get("id");

        Map done = waitUntilFinished(admin, readingId);
        assertThat(done).containsEntry("status", "DONE").containsEntry("source", "TEXT");
        assertThat(LAST.get().text()).isEqualTo(message);
        assertThat(LAST.get().isText()).isTrue();
        assertThat(readings.findById(UUID.fromString(readingId)).orElseThrow().getSourceText())
                .as("o texto colado não fica guardado")
                .isNull();
    }

    @Test
    void refusesWhatItCannotReadAndWhoCannotEdit() {
        String admin = admin();
        Map contract = contract(admin);
        String contractId = (String) contract.get("id");
        String tenderId = (String) contract.get("tenderId");
        String viewer = userToken("leitor.pedido@test.local", Role.VIEWER);

        String edital = document(admin, tenderId, "EDITAL", "edital.pdf", PDF);
        ResponseEntity<Map> wrongKind = send(
                HttpMethod.POST,
                admin,
                "/api/v1/contracts/" + contractId + "/order-readings",
                Map.of("documentId", edital));
        assertThat(wrongKind.getStatusCode().value()).isEqualTo(422);
        assertThat(wrongKind.getBody()).containsEntry("rule", "AI_KIND_NOT_SUPPORTED");

        ResponseEntity<Map> empty = send(
                HttpMethod.POST, admin, "/api/v1/contracts/" + contractId + "/order-readings", Map.of("text", " "));
        assertThat(empty.getBody()).containsEntry("rule", "AI_TEXT_EMPTY");

        assertThat(send(
                                HttpMethod.POST,
                                viewer,
                                "/api/v1/contracts/" + contractId + "/order-readings",
                                Map.of("text", "Pedimos 10 banners, por favor."))
                        .getStatusCode()
                        .value())
                .isEqualTo(403);

        // documento de outra licitação não vira origem de pedido
        ResponseEntity<Map> foreign = send(
                HttpMethod.POST,
                admin,
                "/api/v1/contracts/" + contractId + "/orders",
                Map.of(
                        "version",
                        contract.get("version"),
                        "orderedAt",
                        "2026-10-20",
                        "lines",
                        List.of(Map.of(
                                "itemId",
                                ((List<Map>) contract.get("items")).get(0).get("id"),
                                "quantity",
                                1)),
                        "sourceDocumentId",
                        UUID.randomUUID().toString()));
        assertThat(foreign.getStatusCode().value()).isEqualTo(422);
        assertThat(foreign.getBody()).containsEntry("rule", "SOURCE_DOCUMENT_NOT_FOUND");
    }
}
