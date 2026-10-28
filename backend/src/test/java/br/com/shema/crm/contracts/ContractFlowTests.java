package br.com.shema.crm.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.shema.crm.TestcontainersConfiguration;
import br.com.shema.crm.identity.domain.Role;
import br.com.shema.crm.identity.domain.User;
import br.com.shema.crm.identity.infrastructure.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

/** Contratos, pedidos e saldo pela API, contra Postgres real e com o seed de desenvolvimento carregado. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "shema.bootstrap-admin.email=admin@test.local",
            "shema.bootstrap-admin.password=Senha-forte-123",
            "shema.auth.login-attempts-per-minute=1000"
        })
@SuppressWarnings({"unchecked", "rawtypes"})
class ContractFlowTests {

    /** Contrato da SEFAZ no seed: R$ 237.000,00 com R$ 94.800,00 já pedidos. */
    private static final String SEFAZ_TENDER = "20000000-0000-4000-8000-000000000013";

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

    private ResponseEntity<Map> send(String method, String token, String uri, Object body, String idempotencyKey) {
        var request = http.method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(uri)
                .header("Authorization", "Bearer " + token);
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return request.retrieve().toEntity(Map.class);
    }

    private ResponseEntity<Map> post(String token, String uri, Object body) {
        return send("POST", token, uri, body, null);
    }

    private <T> T get(String token, String uri, Class<T> type) {
        return http.get()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toEntity(type)
                .getBody();
    }

    /** Licitação nova, já vencida, num órgão novo. */
    private String wonTender(String token, String ownerId) {
        String agencyId = (String) post(
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
        Map<String, Object> tender = new HashMap<>(Map.of(
                "agencyId", agencyId,
                "processNumber", "39/2026",
                "object", "Material gráfico",
                "materialType", "Material gráfico",
                "totalValue", 300000.00));
        if (ownerId != null) {
            tender.put("ownerId", ownerId);
        }
        return (String) post(
                        token,
                        "/api/v1/tenders",
                        Map.of("initialStatus", "VENCIDA", "wonAt", "2026-04-10", "tender", tender))
                .getBody()
                .get("id");
    }

    private static BigDecimal money(Object value) {
        return new BigDecimal(value.toString());
    }

    @Test
    void signOrderAndCloseDriveTheTenderAndTheBalance() {
        String token = admin();
        String tenderId = wonTender(token, null);

        ResponseEntity<Map> signed = post(
                token,
                "/api/v1/tenders/" + tenderId + "/contracts",
                Map.of(
                        "contract",
                        Map.of("kind", "CONTRATO", "number", "12/2026", "signedAt", "2026-05-16", "value", 300000.00),
                        "items",
                        List.of(
                                Map.of("description", "Banner", "unit", "un", "quantity", 2000, "unitPrice", 100),
                                Map.of("description", "Adesivo perfurado", "quantity", 4000, "unitPrice", 25))));
        assertThat(signed.getStatusCode().value()).isEqualTo(201);
        Map contract = signed.getBody();
        String contractId = (String) contract.get("id");
        assertThat(money(contract.get("itemsTotal"))).isEqualByComparingTo("300000");
        assertThat(get(token, "/api/v1/tenders/" + tenderId, Map.class)).containsEntry("status", "CONTRATO_ASSINADO");

        List<Map> items = (List<Map>) contract.get("items");
        Map<String, Object> order = Map.of(
                "version",
                contract.get("version"),
                "orderedAt",
                "2026-06-01",
                "reference",
                "AF 123",
                "lines",
                List.of(
                        Map.of("itemId", items.get(0).get("id"), "quantity", 800),
                        Map.of("itemId", items.get(1).get("id"), "quantity", 1600)));
        ResponseEntity<Map> ordered =
                send("POST", token, "/api/v1/contracts/" + contractId + "/orders", order, "pedido-1-" + contractId);
        assertThat(ordered.getStatusCode().value()).isEqualTo(201);
        assertThat(money(ordered.getBody().get("ordered"))).isEqualByComparingTo("120000");
        assertThat(money(ordered.getBody().get("remaining"))).isEqualByComparingTo("180000");
        assertThat(ordered.getBody().get("percentOrdered")).isEqualTo(40);
        assertThat(get(token, "/api/v1/tenders/" + tenderId, Map.class))
                .containsEntry("status", "EM_ANDAMENTO")
                .containsEntry("ordersStartedAt", "2026-06-01");

        // Mesma Idempotency-Key: não lança de novo, mesmo com a versão antiga
        ResponseEntity<Map> replay =
                send("POST", token, "/api/v1/contracts/" + contractId + "/orders", order, "pedido-1-" + contractId);
        assertThat(replay.getStatusCode().value()).isEqualTo(201);
        assertThat((List<?>) replay.getBody().get("orders")).hasSize(1);

        // Sem a chave e com a versão antiga: conflito
        ResponseEntity<Map> stale = send("POST", token, "/api/v1/contracts/" + contractId + "/orders", order, null);
        assertThat(stale.getStatusCode().value()).isEqualTo(409);

        // Acima do saldo do item
        Object version = ordered.getBody().get("version");
        ResponseEntity<Map> tooMuch = post(
                token,
                "/api/v1/contracts/" + contractId + "/orders",
                Map.of(
                        "version",
                        version,
                        "orderedAt",
                        "2026-07-01",
                        "lines",
                        List.of(Map.of("itemId", items.get(0).get("id"), "quantity", 1201))));
        assertThat(tooMuch.getStatusCode().value()).isEqualTo(422);
        assertThat(tooMuch.getBody()).containsEntry("rule", "BALANCE_EXCEEDED");

        List<Map> totals = get(token, "/api/v1/contracts/totals?tenderIds=" + tenderId, List.class);
        assertThat(totals).singleElement().satisfies(t -> {
            assertThat(money(t.get("remaining"))).isEqualByComparingTo("180000");
            assertThat(t.get("lastOrderAt")).isEqualTo("2026-06-01");
            assertThat(((Number) t.get("openContracts")).intValue()).isEqualTo(1);
        });

        ResponseEntity<Map> closed = post(
                token,
                "/api/v1/contracts/" + contractId + "/close",
                Map.of(
                        "version",
                        version,
                        "closedAt",
                        LocalDate.now().minusDays(1).toString()));
        assertThat(closed.getStatusCode().value()).isEqualTo(200);
        assertThat(get(token, "/api/v1/tenders/" + tenderId, Map.class)).containsEntry("status", "ENCERRADO");

        List<Map> history = get(token, "/api/v1/tenders/" + tenderId + "/contracts/history", List.class);
        assertThat(history).extracting(h -> h.get("type")).containsExactly("CLOSED", "ORDER_PLACED", "SIGNED");
        assertThat(history.get(1).get("summary")).isEqualTo("Pedido de R$ 120.000,00 · AF 123");
        List<Map> tenderHistory = get(token, "/api/v1/tenders/" + tenderId + "/history", List.class);
        assertThat(tenderHistory)
                .extracting(h -> h.get("toStatus"))
                .containsExactly("ENCERRADO", "EM_ANDAMENTO", "CONTRATO_ASSINADO", "VENCIDA");
    }

    @Test
    void tenderOnlyClosesWhenTheLastContractCloses() {
        String token = admin();
        String tenderId = wonTender(token, null);
        Map first = post(
                        token,
                        "/api/v1/tenders/" + tenderId + "/contracts",
                        Map.of("contract", Map.of("kind", "EMPENHO", "signedAt", "2026-05-16", "value", 1000)))
                .getBody();
        Map second = post(
                        token,
                        "/api/v1/tenders/" + tenderId + "/contracts",
                        Map.of("contract", Map.of("kind", "EMPENHO", "signedAt", "2026-06-20", "value", 2000)))
                .getBody();
        assertThat(get(token, "/api/v1/tenders/" + tenderId, Map.class)).containsEntry("signedAt", "2026-05-16");

        post(
                token,
                "/api/v1/contracts/" + first.get("id") + "/close",
                Map.of("version", first.get("version"), "closedAt", "2026-08-01"));
        assertThat(get(token, "/api/v1/tenders/" + tenderId, Map.class)).containsEntry("status", "CONTRATO_ASSINADO");

        post(
                token,
                "/api/v1/contracts/" + second.get("id") + "/close",
                Map.of("version", second.get("version"), "closedAt", "2026-09-01"));
        assertThat(get(token, "/api/v1/tenders/" + tenderId, Map.class))
                .containsEntry("status", "ENCERRADO")
                .containsEntry("closedAt", "2026-09-01");
    }

    @Test
    void onlyWonTendersReceiveContracts() {
        String token = admin();
        String agencyId = (String) post(
                        token,
                        "/api/v1/agencies",
                        Map.of(
                                "name",
                                "Prefeitura de Paracambi " + UUID.randomUUID(),
                                "sphere",
                                "MUNICIPAL",
                                "city",
                                "Paracambi",
                                "state",
                                "RJ"))
                .getBody()
                .get("id");
        String disputeId = (String) post(
                        token,
                        "/api/v1/tenders",
                        Map.of(
                                "initialStatus",
                                "EM_DISPUTA",
                                "tender",
                                Map.of(
                                        "agencyId", agencyId,
                                        "processNumber", "1/2026",
                                        "object", "Lixeiras",
                                        "materialType", "Lixeiras",
                                        "totalValue", 1000)))
                .getBody()
                .get("id");
        ResponseEntity<Map> refused = post(
                token,
                "/api/v1/tenders/" + disputeId + "/contracts",
                Map.of("contract", Map.of("signedAt", "2026-05-16", "value", 1000)));
        assertThat(refused.getStatusCode().value()).isEqualTo(422);
        assertThat(refused.getBody()).containsEntry("rule", "TENDER_NOT_CONTRACTABLE");
    }

    @Test
    void seedShowsTheBalanceAsInTheSpreadsheet() {
        String token = admin();
        List<Map> contracts = get(token, "/api/v1/tenders/" + SEFAZ_TENDER + "/contracts", List.class);
        assertThat(contracts).singleElement().satisfies(c -> {
            assertThat(money(c.get("value"))).isEqualByComparingTo("237000");
            assertThat(money(c.get("ordered"))).isEqualByComparingTo("94800");
            assertThat(money(c.get("remaining"))).isEqualByComparingTo("142200");
            List<Map> items = (List<Map>) c.get("items");
            assertThat(items)
                    .extracting(
                            i -> money(i.get("remaining")).stripTrailingZeros().toPlainString())
                    .containsExactly("3000", "1200", "600", "75");
        });
        Map summary = get(token, "/api/v1/contracts/summary", Map.class);
        assertThat(((Number) summary.get("contracts")).intValue()).isGreaterThanOrEqualTo(7);
    }

    @Test
    void salesEditsOnlyContractsOfOwnTenders() {
        String admin = admin();
        if (users.findByEmail("vendedor.c@test.local").isEmpty()) {
            users.save(new User(
                    "Vendedor C", "vendedor.c@test.local", passwordEncoder.encode("Senha-venda-1"), Role.SALES));
        }
        String sales = token("vendedor.c@test.local", "Senha-venda-1");
        String othersTender = wonTender(admin, "00000000-0000-4000-8000-000000000001");
        ResponseEntity<Map> refused = post(
                sales,
                "/api/v1/tenders/" + othersTender + "/contracts",
                Map.of("contract", Map.of("signedAt", "2026-05-16", "value", 1000)));
        assertThat(refused.getStatusCode().value()).isEqualTo(403);

        List<Map> seen = get(sales, "/api/v1/tenders/" + SEFAZ_TENDER + "/contracts", List.class);
        assertThat(seen.getFirst().get("canEdit")).isEqualTo(false);
    }
}
