package br.com.shema.crm.tenders;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.shema.crm.TestcontainersConfiguration;
import br.com.shema.crm.identity.domain.Role;
import br.com.shema.crm.identity.domain.User;
import br.com.shema.crm.identity.infrastructure.UserRepository;
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

/** Fluxo de licitações pela API, contra Postgres real e com o seed de desenvolvimento carregado. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "shema.bootstrap-admin.email=admin@test.local",
            "shema.bootstrap-admin.password=Senha-forte-123",
            "shema.auth.login-attempts-per-minute=1000"
        })
@SuppressWarnings({"unchecked", "rawtypes"})
class TenderFlowTests {

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

    private String salesUser(String email) {
        if (users.findByEmail(email).isEmpty()) {
            users.save(new User("Vendedor " + email, email, passwordEncoder.encode("Senha-venda-1"), Role.SALES));
        }
        return token(email, "Senha-venda-1");
    }

    private ResponseEntity<Map> post(String token, String uri, Object body) {
        return http.post()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(Map.class);
    }

    private ResponseEntity<Map> put(String token, String uri, Object body) {
        return http.put()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(Map.class);
    }

    private <T> ResponseEntity<T> get(String token, String uri, Class<T> type) {
        return http.get()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toEntity(type);
    }

    private String createAgency(String token, String name) {
        ResponseEntity<Map> created = post(
                token,
                "/api/v1/agencies",
                Map.of("name", name, "sphere", "MUNICIPAL", "city", "Itaguaí", "state", "RJ"));
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        return (String) created.getBody().get("id");
    }

    private Map<String, Object> tenderBody(String agencyId, Object ownerId) {
        Map<String, Object> body = new java.util.HashMap<>(Map.of(
                "agencyId", agencyId,
                "modality", "PREGAO_ELETRONICO",
                "processNumber", "39/2026",
                "portal", "Compras.gov.br",
                "object", "Material gráfico para campanhas",
                "materialType", "Material gráfico",
                "itemsQuantity", 500,
                "totalValue", 300000.00));
        if (ownerId != null) {
            body.put("ownerId", ownerId);
        }
        return body;
    }

    @Test
    void seedLoadsAndSummaryFollowsFilters() {
        String token = admin();
        List<Map> summary = get(token, "/api/v1/tenders/summary", List.class).getBody();
        Map vencidas = summary.stream()
                .filter(s -> s.get("status").equals("VENCIDA"))
                .findFirst()
                .orElseThrow();
        assertThat(((Number) vencidas.get("count")).intValue()).isGreaterThanOrEqualTo(6);

        List<Map> filtered =
                get(token, "/api/v1/tenders/summary?q=mesquita", List.class).getBody();
        long total = filtered.stream()
                .mapToLong(s -> ((Number) s.get("count")).longValue())
                .sum();
        assertThat(total).isEqualTo(1);

        Map page = get(
                        token,
                        "/api/v1/tenders?status=CONTRATO_ASSINADO&signedBefore=2026-09-30&sort=signedAt&direction=asc",
                        Map.class)
                .getBody();
        List<Map> items = (List<Map>) page.get("items");
        assertThat(items).isNotEmpty();
        assertThat(((Map) items.getFirst().get("agency")).get("name")).isEqualTo("SEGOV · Secretaria de Governo");
        assertThat(items.getFirst().get("reference")).isEqualTo("UASG 926105 / PE 031/2026");
        assertThat(((Map) items.getFirst().get("owner")).get("name")).isEqualTo("Edwin Lima");
    }

    @Test
    void registerEditAndWalkTheLifecycleWithHistory() {
        String token = admin();
        String agencyId = createAgency(token, "Prefeitura Municipal de Itaguaí " + UUID.randomUUID());

        ResponseEntity<Map> created = post(
                token,
                "/api/v1/tenders",
                Map.of("initialStatus", "VENCIDA", "wonAt", "2026-10-01", "tender", tenderBody(agencyId, null)));
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        Map tender = created.getBody();
        String id = (String) tender.get("id");
        assertThat(tender.get("status")).isEqualTo("VENCIDA");
        assertThat(tender.get("reference")).isEqualTo("PE 039/2026");
        assertThat(tender.get("canEdit")).isEqualTo(true);
        // "Contrato assinado" sai das ações da licitação: é registrado pelo contrato (ADR 0012)
        assertThat((List<Map>) tender.get("nextStatuses"))
                .extracting(s -> s.get("status"))
                .containsExactly("AGUARDANDO_ASSINATURA");

        Map<String, Object> edited = tenderBody(agencyId, null);
        edited.put("totalValue", 320000.00);
        ResponseEntity<Map> updated =
                put(token, "/api/v1/tenders/" + id, Map.of("version", tender.get("version"), "tender", edited));
        assertThat(updated.getStatusCode().value()).isEqualTo(200);

        // Versão antiga: conflito
        ResponseEntity<Map> stale =
                put(token, "/api/v1/tenders/" + id, Map.of("version", tender.get("version"), "tender", edited));
        assertThat(stale.getStatusCode().value()).isEqualTo(409);
        assertThat(stale.getBody()).containsEntry("code", "VERSION_CONFLICT");

        // Assinar pela licitação não vale mais: a assinatura é o registro do contrato
        Object version = updated.getBody().get("version");
        ResponseEntity<Map> manual = post(
                token,
                "/api/v1/tenders/" + id + "/transitions",
                Map.of("version", version, "target", "CONTRATO_ASSINADO", "date", "2026-10-15"));
        assertThat(manual.getStatusCode().value()).isEqualTo(422);
        assertThat(manual.getBody()).containsEntry("rule", "MANAGED_BY_CONTRACTS");

        // Assinar sem data: o contrato recusa
        ResponseEntity<Map> noDate = post(
                token,
                "/api/v1/tenders/" + id + "/contracts",
                Map.of("contract", Map.of("kind", "CONTRATO", "value", 320000.00)));
        assertThat(noDate.getStatusCode().value()).isEqualTo(400);
        assertThat(noDate.getBody()).containsEntry("code", "VALIDATION_ERROR");

        ResponseEntity<Map> contract = post(
                token,
                "/api/v1/tenders/" + id + "/contracts",
                Map.of("contract", Map.of("kind", "CONTRATO", "signedAt", "2026-10-15", "value", 320000.00)));
        assertThat(contract.getStatusCode().value()).isEqualTo(201);
        assertThat(get(token, "/api/v1/tenders/" + id, Map.class).getBody())
                .containsEntry("status", "CONTRATO_ASSINADO")
                .containsEntry("signedAt", "2026-10-15");

        List<Map> history =
                get(token, "/api/v1/tenders/" + id + "/history", List.class).getBody();
        assertThat(history).extracting(h -> h.get("type")).containsExactly("STATUS_CHANGED", "UPDATED", "CREATED");
        List<Map> changes = (List<Map>) history.get(1).get("changes");
        assertThat(changes).singleElement().satisfies(c -> {
            assertThat(c.get("field")).isEqualTo("Valor");
            assertThat(c.get("from")).isEqualTo("300000.00");
            assertThat(c.get("to")).isEqualTo("320000.00");
        });
    }

    @Test
    void salesEditsOnlyOwnAndViewerNeverWrites() {
        String admin = admin();
        String agencyId = createAgency(admin, "Prefeitura de Seropédica " + UUID.randomUUID());
        String salesA = salesUser("vendedor.a@test.local");
        String salesB = salesUser("vendedor.b@test.local");

        Map tender = post(
                        salesA,
                        "/api/v1/tenders",
                        Map.of("initialStatus", "EM_DISPUTA", "tender", tenderBody(agencyId, null)))
                .getBody();
        String id = (String) tender.get("id");
        assertThat(((Map) tender.get("owner")).get("name")).isEqualTo("Vendedor vendedor.a@test.local");

        ResponseEntity<Map> byOther = post(
                salesB,
                "/api/v1/tenders/" + id + "/transitions",
                Map.of("version", tender.get("version"), "target", "PERDIDA"));
        assertThat(byOther.getStatusCode().value()).isEqualTo(403);

        Map seenByB = get(salesB, "/api/v1/tenders/" + id, Map.class).getBody();
        assertThat(seenByB.get("canEdit")).isEqualTo(false);

        users.save(new User("Leitor", "leitor.f1@test.local", passwordEncoder.encode("Senha-leitor-1"), Role.VIEWER));
        String viewer = token("leitor.f1@test.local", "Senha-leitor-1");
        assertThat(post(
                                viewer,
                                "/api/v1/tenders",
                                Map.of("initialStatus", "EM_DISPUTA", "tender", tenderBody(agencyId, null)))
                        .getStatusCode()
                        .value())
                .isEqualTo(403);
        assertThat(get(viewer, "/api/v1/tenders?q=Seropédica", Map.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(200);
    }

    @Test
    void validationAndDomainErrorsAreClear() {
        String token = admin();
        ResponseEntity<Map> invalid =
                post(token, "/api/v1/tenders", Map.of("initialStatus", "EM_DISPUTA", "tender", Map.of()));
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        assertThat(invalid.getBody()).containsEntry("code", "VALIDATION_ERROR");

        String agencyId = createAgency(token, "Prefeitura de Japeri " + UUID.randomUUID());
        Map<String, Object> noId = tenderBody(agencyId, null);
        noId.remove("processNumber");
        ResponseEntity<Map> rule =
                post(token, "/api/v1/tenders", Map.of("initialStatus", "EM_DISPUTA", "tender", noId));
        assertThat(rule.getStatusCode().value()).isEqualTo(422);
        assertThat(rule.getBody()).containsEntry("rule", "IDENTIFICATION_REQUIRED");

        ResponseEntity<Map> duplicate = post(
                token,
                "/api/v1/agencies",
                Map.of("name", "Prefeitura Municipal de Magé", "sphere", "MUNICIPAL", "city", "Magé", "state", "RJ"));
        assertThat(duplicate.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void adminManagesUsers() {
        String token = admin();
        ResponseEntity<Map> created = post(
                token,
                "/api/v1/users",
                Map.of(
                        "name",
                        "Nova Vendedora",
                        "email",
                        "nova@test.local",
                        "role",
                        "SALES",
                        "password",
                        "Senha-inicial-1"));
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        String id = (String) created.getBody().get("id");

        assertThat(post(
                                token,
                                "/api/v1/users",
                                Map.of(
                                        "name",
                                        "Fraca",
                                        "email",
                                        "fraca@test.local",
                                        "role",
                                        "SALES",
                                        "password",
                                        "curta"))
                        .getStatusCode()
                        .value())
                .isEqualTo(400);

        assertThat(token("nova@test.local", "Senha-inicial-1")).isNotBlank();
        ResponseEntity<Map> deactivated =
                put(token, "/api/v1/users/" + id, Map.of("name", "Nova Vendedora", "role", "SALES", "active", false));
        assertThat(deactivated.getBody()).containsEntry("active", false);

        List<Map> options = get(token, "/api/v1/users/options", List.class).getBody();
        assertThat(options)
                .extracting(o -> o.get("name"))
                .contains("Edwin Lima")
                .doesNotContain("Nova Vendedora");
    }

    @Test
    void agencyContactsAreNormalizedAndVersioned() {
        String token = admin();
        String agencyId = createAgency(token, "Prefeitura de Japeri " + UUID.randomUUID());
        Map<String, Object> current = get(token, "/api/v1/agencies/" + agencyId + "/contacts", Map.class)
                .getBody();
        assertThat((List<?>) current.get("contacts")).isEmpty();

        ResponseEntity<Map> saved = put(
                token,
                "/api/v1/agencies/" + agencyId + "/contacts",
                Map.of(
                        "version",
                        current.get("agencyVersion"),
                        "contacts",
                        List.of(Map.of(
                                "name", "Ana Paula",
                                "role", "Pregoeira",
                                "email", "Ana.Paula@Japeri.RJ.gov.br",
                                "phone", "+55 (21) 98765-4321"))));
        assertThat(saved.getStatusCode().value()).isEqualTo(200);
        assertThat((List<Map>) saved.getBody().get("contacts")).singleElement().satisfies(c -> {
            assertThat(c.get("email")).isEqualTo("ana.paula@japeri.rj.gov.br");
            assertThat(c.get("phone")).isEqualTo("21987654321");
        });

        ResponseEntity<Map> stale = put(
                token,
                "/api/v1/agencies/" + agencyId + "/contacts",
                Map.of("version", current.get("agencyVersion"), "contacts", List.of()));
        assertThat(stale.getStatusCode().value()).isEqualTo(409);

        ResponseEntity<Map> badEmail = put(
                token,
                "/api/v1/agencies/" + agencyId + "/contacts",
                Map.of(
                        "version",
                        saved.getBody().get("agencyVersion"),
                        "contacts",
                        List.of(Map.of("name", "João", "email", "joao-sem-arroba"))));
        assertThat(badEmail.getStatusCode().value()).isEqualTo(422);
        assertThat(badEmail.getBody()).containsEntry("rule", "EMAIL_INVALID");
    }
}
