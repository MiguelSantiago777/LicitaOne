package br.com.shema.crm.proposals;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.shema.crm.TestcontainersConfiguration;
import br.com.shema.crm.identity.domain.Role;
import br.com.shema.crm.identity.domain.User;
import br.com.shema.crm.identity.infrastructure.UserRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

/** Propostas de preços pela API, contra Postgres real: número fixo, salvamento, PDF, cancelamento e permissões. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "shema.bootstrap-admin.email=admin@test.local",
            "shema.bootstrap-admin.password=Senha-forte-123",
            "shema.auth.login-attempts-per-minute=1000",
            "shema.company.cnpj=11.222.333/0001-81",
            "shema.company.city=Rio de Janeiro"
        })
@SuppressWarnings({"unchecked", "rawtypes"})
class ProposalFlowTests {

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

    private String userToken(String name, String email, Role role) {
        if (users.findByEmail(email).isEmpty()) {
            users.save(new User(name, email, passwordEncoder.encode("Senha-teste-123"), role));
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

    private Map ok(ResponseEntity<Map> response, int status) {
        assertThat(response.getStatusCode().value())
                .as("%s", response.getBody())
                .isEqualTo(status);
        return response.getBody();
    }

    private static Map content(String client, List<Map> items) {
        Map body = new HashMap();
        body.put("kind", "PRODUTO");
        body.put("clientName", client);
        body.put("clientCnpj", "11.222.333/0001-81");
        body.put("clientAddress", "Rua da Assembleia, 10, Centro, Rio de Janeiro/RJ");
        body.put("clientEmail", "compras@cliente.com.br");
        body.put("clientPhones", "(21) 2222-0000 / (21) 99999-0000");
        body.put("validityDays", 60);
        body.put("paymentTerms", "30 dias após a nota fiscal");
        body.put("deliveryTerms", "10 dias úteis após o pedido");
        body.put("proposalDate", LocalDate.now().toString());
        body.put("signerName", "Ana Souza");
        body.put("signerRole", "Gerente comercial");
        body.put("signerCpf", "529.982.247-25");
        body.put("items", items);
        return body;
    }

    private static Map item(String description, String unit, String brand, String quantity, String unitPrice) {
        Map item = new HashMap();
        item.put("description", description);
        item.put("unit", unit);
        item.put("brand", brand);
        item.put("quantity", quantity);
        item.put("unitPrice", unitPrice);
        return item;
    }

    private static Map update(Map proposal, Map content) {
        return Map.of("version", proposal.get("version"), "proposal", content);
    }

    @Test
    void createFillDownloadAndKeepTheNumber() {
        String admin = token("admin@test.local", "Senha-forte-123");
        ok(
                send(
                        HttpMethod.PUT,
                        admin,
                        "/api/v1/me/profile",
                        Map.of("jobTitle", "Diretor", "cpf", "529.982.247-25")),
                200);

        Map created = ok(send(HttpMethod.POST, admin, "/api/v1/proposals", null), 201);
        String number = (String) created.get("number");
        assertThat(number).matches(LocalDate.now().getYear() + "/\\d{4}");
        assertThat(created.get("signerRole")).isEqualTo("Diretor");
        assertThat(created.get("signerCpf")).isEqualTo("52998224725");
        assertThat((List<String>) created.get("missing")).contains("nome do cliente");
        String id = (String) created.get("id");

        // Incompleta: o PDF diz o que falta
        Map incomplete = ok(send(HttpMethod.GET, admin, "/api/v1/proposals/" + id + "/pdf", null), 422);
        assertThat((String) incomplete.get("detail")).contains("nome do cliente");

        Map saved = ok(
                send(
                        HttpMethod.PUT,
                        admin,
                        "/api/v1/proposals/" + id,
                        update(
                                created,
                                content(
                                        "Construtora Horizonte Ltda",
                                        List.of(
                                                item(
                                                        "Papel A4 75 g, resma com 500 folhas",
                                                        "resma",
                                                        "Chamex",
                                                        "10",
                                                        "25.90"),
                                                item("Toner compatível HP 105A", "un", null, "2", "189.5"))))),
                200);
        assertThat(saved.get("number")).isEqualTo(number);
        assertThat(((Number) saved.get("total")).doubleValue()).isEqualTo(638.0);
        assertThat(saved.get("totalInWords")).isEqualTo("seiscentos e trinta e oito reais");
        assertThat((List<?>) saved.get("missing")).isEmpty();

        ResponseEntity<byte[]> pdf = http.get()
                .uri("/api/v1/proposals/" + id + "/pdf")
                .header("Authorization", "Bearer " + admin)
                .retrieve()
                .toEntity(byte[].class);
        assertThat(pdf.getStatusCode().value()).isEqualTo(200);
        assertThat(pdf.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(pdf.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment", "Proposta-" + number.replace('/', '-') + ".pdf");
        assertThat(new String(pdf.getBody(), 0, 5)).isEqualTo("%PDF-");

        // Busca pelo número e pelo CNPJ
        Map byNumber = ok(send(HttpMethod.GET, admin, "/api/v1/proposals?q=" + number, null), 200);
        assertThat((List<Map>) byNumber.get("items"))
                .extracting(m -> m.get("id"))
                .containsExactly(id);
        Map byCnpj = ok(send(HttpMethod.GET, admin, "/api/v1/proposals?q=11.222.333", null), 200);
        assertThat((List<Map>) byCnpj.get("items")).extracting(m -> m.get("id")).contains(id);

        // Versão antiga: conflito, nada muda
        ok(send(HttpMethod.PUT, admin, "/api/v1/proposals/" + id, update(created, content("Outro", List.of()))), 409);
    }

    @Test
    void duplicateCancelAndReopen() {
        String sales = userToken("Bruno Vendas", "bruno.propostas@test.local", Role.SALES);
        Map created = ok(send(HttpMethod.POST, sales, "/api/v1/proposals", null), 201);
        String id = (String) created.get("id");
        Map saved = ok(
                send(
                        HttpMethod.PUT,
                        sales,
                        "/api/v1/proposals/" + id,
                        update(
                                created,
                                content("Hospital Central", List.of(item("Luva nitrílica", "cx", null, "5", "40"))))),
                200);

        Map copy = ok(send(HttpMethod.POST, sales, "/api/v1/proposals/" + id + "/duplicate", null), 201);
        assertThat(copy.get("number")).isNotEqualTo(saved.get("number"));
        assertThat(copy.get("clientName")).isEqualTo("Hospital Central");
        assertThat((List<?>) copy.get("items")).hasSize(1);

        Map cancelled = ok(
                send(
                        HttpMethod.POST,
                        sales,
                        "/api/v1/proposals/" + id + "/cancel",
                        Map.of("version", saved.get("version"))),
                200);
        assertThat(cancelled.get("cancelledAt")).isNotNull();
        assertThat(cancelled.get("canEdit")).isEqualTo(false);
        ok(send(HttpMethod.PUT, sales, "/api/v1/proposals/" + id, update(cancelled, content("X", List.of()))), 422);
        ok(send(HttpMethod.GET, sales, "/api/v1/proposals/" + id + "/pdf", null), 422);

        Map list = ok(send(HttpMethod.GET, sales, "/api/v1/proposals?q=Hospital Central", null), 200);
        assertThat((List<Map>) list.get("items")).extracting(m -> m.get("id")).doesNotContain(id);
        Map withCancelled = ok(
                send(HttpMethod.GET, sales, "/api/v1/proposals?q=Hospital Central&includeCancelled=true", null), 200);
        assertThat((List<Map>) withCancelled.get("items"))
                .extracting(m -> m.get("id"))
                .contains(id);

        Map reopened = ok(
                send(
                        HttpMethod.POST,
                        sales,
                        "/api/v1/proposals/" + id + "/reopen",
                        Map.of("version", cancelled.get("version"))),
                200);
        assertThat(reopened.get("cancelledAt")).isNull();
        assertThat(reopened.get("number")).isEqualTo(saved.get("number"));
    }

    @Test
    void viewerSeesButCannotCreate() {
        String admin = token("admin@test.local", "Senha-forte-123");
        String viewer = userToken("Vera Leitura", "vera.propostas@test.local", Role.VIEWER);
        Map created = ok(send(HttpMethod.POST, admin, "/api/v1/proposals", null), 201);

        ok(send(HttpMethod.POST, viewer, "/api/v1/proposals", null), 403);
        Map seen = ok(send(HttpMethod.GET, viewer, "/api/v1/proposals/" + created.get("id"), null), 200);
        assertThat(seen.get("canEdit")).isEqualTo(false);
    }

    @Test
    void numbersCreatedAtTheSameTimeAreNeverRepeated() {
        String admin = token("admin@test.local", "Senha-forte-123");
        List<CompletableFuture<Map>> calls = new ArrayList<>();
        IntStream.range(0, 8)
                .forEach(i -> calls.add(CompletableFuture.supplyAsync(
                        () -> ok(send(HttpMethod.POST, admin, "/api/v1/proposals", null), 201))));
        List<Object> numbers = calls.stream()
                .map(CompletableFuture::join)
                .map(m -> m.get("number"))
                .toList();

        assertThat(numbers).doesNotHaveDuplicates().hasSize(8);
    }
}
