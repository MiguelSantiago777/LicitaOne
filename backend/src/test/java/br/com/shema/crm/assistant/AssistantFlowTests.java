package br.com.shema.crm.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.shema.crm.TestcontainersConfiguration;
import br.com.shema.crm.assistant.application.AssistantModel;
import br.com.shema.crm.assistant.application.AssistantTools;
import br.com.shema.crm.identity.domain.Role;
import br.com.shema.crm.identity.domain.User;
import br.com.shema.crm.identity.infrastructure.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * "Pergunte ao LicitaOne" (Fase 11) com banco real e um modelo roteirizado: a consulta roda como quem perguntou, só os
 * ids que vieram das consultas viram link, o limite do dia vale e uma pessoa não vê a pergunta de outra.
 */
@Import({TestcontainersConfiguration.class, AssistantFlowTests.ScriptedModelConfig.class})
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "shema.bootstrap-admin.email=admin@test.local",
            "shema.bootstrap-admin.password=Senha-forte-123",
            "shema.auth.login-attempts-per-minute=1000",
            "shema.assistant.daily-limit=3"
        })
@SuppressWarnings({"unchecked", "rawtypes"})
class AssistantFlowTests {

    /** O que o modelo recebeu em cada rodada (o system e a conversa). */
    static final List<String> SYSTEMS = new CopyOnWriteArrayList<>();

    static final List<List<AssistantModel.Entry>> CONVERSATIONS = new CopyOnWriteArrayList<>();

    /**
     * Pergunta com "falhe": a IA cai. Senão: primeira rodada pede contratos com saldo a partir de R$ 200 mil; a segunda
     * responde com os ids que vieram, mais um inventado.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class ScriptedModelConfig {

        @Bean
        @Primary
        AssistantModel scriptedModel(JsonMapper json) {
            return new AssistantModel() {
                @Override
                public boolean enabled() {
                    return true;
                }

                @Override
                public Reply next(String system, List<Entry> conversation, List<ToolSpec> tools) {
                    SYSTEMS.add(system);
                    CONVERSATIONS.add(conversation);
                    Entry last = conversation.getLast();
                    if (last instanceof UserText u && u.text().contains("falhe")) {
                        throw new ModelFailed("A IA está ocupada ou fora do ar agora.", null);
                    }
                    if (last instanceof UserText) {
                        return new Reply(
                                new AssistantTurn(
                                        "",
                                        List.of(new ToolCall(
                                                "c1", "contratos_abertos", Map.of("saldo_minimo", "200000"))),
                                        null),
                                "roteiro",
                                100,
                                20);
                    }
                    ToolResults results = (ToolResults) last;
                    JsonNode node = json.readTree(results.results().getFirst().content());
                    List<String> ids = new ArrayList<>();
                    node.get("contratos").forEach(c -> ids.add(c.get("id").asString()));
                    ids.add(UUID.randomUUID().toString());
                    return new Reply(
                            new AssistantTurn(
                                    "",
                                    List.of(new ToolCall(
                                            "c2",
                                            "responder",
                                            Map.of(
                                                    "texto",
                                                    node.get("total").asInt() + " contrato(s).",
                                                    "itens",
                                                    ids))),
                                    null),
                            "roteiro",
                            200,
                            40);
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
    AssistantTools tools;

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
            users.save(new User("Pessoa " + role, email, passwordEncoder.encode("Senha-teste-123"), role));
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

    private Map waitFor(String token, String id) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            Map q = send(HttpMethod.GET, token, "/api/v1/assistant/questions/" + id, null)
                    .getBody();
            if (!"RUNNING".equals(q.get("status"))) {
                return q;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("a pergunta não terminou");
    }

    /** Contrato de R$ 300 mil com R$ 50 mil pedidos: saldo de R$ 250 mil. */
    private String contractWithBalance(String token) {
        String agencyId = (String) send(
                        HttpMethod.POST,
                        token,
                        "/api/v1/agencies",
                        Map.of(
                                "name", "Prefeitura de Saquarema " + UUID.randomUUID(),
                                "sphere", "MUNICIPAL",
                                "city", "Saquarema",
                                "state", "RJ"))
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
                                        "processNumber", "77/2026",
                                        "object", "Material de limpeza",
                                        "materialType", "Limpeza",
                                        "totalValue", 300000.00)))
                .getBody()
                .get("id");
        Map contract = send(
                        HttpMethod.POST,
                        token,
                        "/api/v1/tenders/" + tenderId + "/contracts",
                        Map.of(
                                "contract",
                                Map.of(
                                        "kind", "CONTRATO",
                                        "number", "77/2026",
                                        "signedAt", "2026-05-16",
                                        "value", 300000.00),
                                "items",
                                List.of(Map.of("description", "Detergente", "quantity", 3000, "unitPrice", 100))))
                .getBody();
        List<Map> items = (List<Map>) contract.get("items");
        send(
                HttpMethod.POST,
                token,
                "/api/v1/contracts/" + contract.get("id") + "/orders",
                Map.of(
                        "version",
                        contract.get("version"),
                        "orderedAt",
                        "2026-06-01",
                        "lines",
                        List.of(Map.of("itemId", items.get(0).get("id"), "quantity", 500))));
        return (String) contract.get("id");
    }

    @Test
    void answersWithLinksOnlyFromTheQueriesAndRespectsTheDailyLimit() throws InterruptedException {
        String admin = token("admin@test.local", "Senha-forte-123");
        String contractId = contractWithBalance(admin);
        // Leitura também pergunta (decisão de 04/11/2026)
        String viewer = userToken("leitura-assistente@test.local", Role.VIEWER);

        Map settings = send(HttpMethod.GET, viewer, "/api/v1/assistant", null).getBody();
        assertThat(settings)
                .containsEntry("enabled", true)
                .containsEntry("dailyLimit", 3)
                .containsEntry("usedToday", 0);
        assertThat((List) settings.get("examples")).isNotEmpty();

        ResponseEntity<Map> asked = send(
                HttpMethod.POST,
                viewer,
                "/api/v1/assistant/questions",
                Map.of(
                        "question",
                        "Quais contratos têm mais de R$ 200 mil de saldo?",
                        "history",
                        List.of(Map.of("question", "Quantos contratos abertos?", "answer", "Vários."))));
        assertThat(asked.getStatusCode().value()).as("%s", asked.getBody()).isEqualTo(202);
        Map done = waitFor(viewer, (String) asked.getBody().get("id"));
        assertThat(done.get("status")).as("%s", done).isEqualTo("DONE");
        assertThat((List<String>) done.get("steps")).contains("Consultando contratos e saldos…");

        Map answer = (Map) done.get("answer");
        List<Map> items = (List<Map>) answer.get("items");
        assertThat(items).extracting(i -> i.get("id")).contains(contractId).doesNotHaveDuplicates();
        Map item = items.stream()
                .filter(i -> contractId.equals(i.get("id")))
                .findFirst()
                .orElseThrow();
        assertThat(item).containsEntry("type", "CONTRATO").containsEntry("query", Map.of("aba", "itens"));
        assertThat((String) item.get("detail")).contains("saldo R$ 250.000,00");
        assertThat((List<String>) answer.get("warnings"))
                .contains("Um item citado pela IA não veio das consultas e ficou fora da lista.");
        assertThat((List<Map>) answer.get("queries"))
                .singleElement()
                .satisfies(q -> assertThat(q)
                        .containsEntry("label", "Contratos abertos")
                        .containsEntry("filters", "saldo a partir de R$ 200.000,00"));

        // O modelo recebeu a conversa anterior, a data de hoje e quem perguntou
        assertThat(SYSTEMS.getLast()).contains("Hoje é").contains("Pessoa VIEWER");
        assertThat(CONVERSATIONS.getFirst().getFirst())
                .isEqualTo(new AssistantModel.UserText("Quantos contratos abertos?"));

        // Outra pessoa não vê a pergunta
        String sales = userToken("comercial-assistente@test.local", Role.SALES);
        assertThat(send(
                                HttpMethod.GET,
                                sales,
                                "/api/v1/assistant/questions/" + asked.getBody().get("id"),
                                null)
                        .getStatusCode()
                        .value())
                .isEqualTo(404);

        // IA fora do ar: a pergunta termina com a mensagem
        Map failed = waitFor(viewer, (String)
                send(HttpMethod.POST, viewer, "/api/v1/assistant/questions", Map.of("question", "falhe agora"))
                        .getBody()
                        .get("id"));
        assertThat(failed)
                .containsEntry("status", "FAILED")
                .containsEntry("error", "A IA está ocupada ou fora do ar agora.");

        // Limite do dia: 3 perguntas; a quarta é recusada e não conta
        waitFor(viewer, (String)
                send(HttpMethod.POST, viewer, "/api/v1/assistant/questions", Map.of("question", "E hoje?"))
                        .getBody()
                        .get("id"));
        ResponseEntity<Map> over =
                send(HttpMethod.POST, viewer, "/api/v1/assistant/questions", Map.of("question", "Mais uma?"));
        assertThat(over.getStatusCode().value()).isEqualTo(422);
        assertThat(over.getBody()).containsEntry("rule", "ASSISTANT_LIMIT");
        assertThat(send(HttpMethod.GET, viewer, "/api/v1/assistant", null).getBody())
                .containsEntry("usedToday", 3);
    }

    /** Cada consulta roda contra o seed, como um usuário logado, e devolve itens com rota; parâmetro ruim vira erro. */
    @Test
    void everyQueryRunsAgainstTheSeedAndBadArgumentsComeBackAsErrors() {
        var auth = new org.springframework.security.authentication.TestingAuthenticationToken(
                org.springframework.security.oauth2.jwt.Jwt.withTokenValue("t")
                        .header("alg", "none")
                        .subject(UUID.randomUUID().toString())
                        .claim("name", "Teste")
                        .claim("roles", List.of("ADMIN"))
                        .build(),
                null,
                "ROLE_ADMIN");
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(auth);
        try {
            Map<String, String> types = Map.of(
                    "buscar_licitacoes", "LICITACAO",
                    "contratos_abertos", "CONTRATO",
                    "buscar_atas", "ATA",
                    "municipios_sem_ata", "ORGAO",
                    "buscar_orgaos", "ORGAO",
                    "buscar_negociacoes", "NEGOCIACAO");
            Map<String, Map<String, Object>> inputs = Map.of(
                    "buscar_licitacoes", Map.of("situacoes", List.of("VENCIDA", "EM_ANDAMENTO", "inventada")),
                    "contratos_abertos", Map.of(),
                    "buscar_atas", Map.of("filtro", "TODAS"),
                    "municipios_sem_ata", Map.of("uf", "rj"),
                    "buscar_orgaos", Map.of("texto", "prefeitura"),
                    "buscar_negociacoes", Map.of());
            types.forEach((tool, type) -> {
                AssistantTools.Output out = tools.run(tool, inputs.get(tool));
                assertThat(out.error()).as("%s: %s", tool, out.content()).isFalse();
                assertThat(out.refs()).as(tool).isNotEmpty().allSatisfy(r -> {
                    assertThat(r.type()).isEqualTo(type);
                    assertThat(r.path()).startsWith("/");
                });
                assertThat(out.log()).isNotNull();
            });
            assertThat(tools.run("buscar_tarefas", Map.of("situacao", "TODAS")).error())
                    .isFalse();
            assertThat(tools.run("buscar_pessoas", Map.of("texto", "admin")).error())
                    .isFalse();

            assertThat(tools.run("detalhe_contrato", Map.of("contrato_id", "não-é-id"))
                            .content())
                    .contains("não é um id válido");
            assertThat(tools.run("contratos_abertos", Map.of("saldo_minimo", "R$ 100 mil"))
                            .content())
                    .contains("deve ser um número");
            assertThat(tools.run("buscar_atas", Map.of("vence_ate", "março")).content())
                    .contains("AAAA-MM-DD");
            assertThat(tools.run("apagar_tudo", Map.of()).error()).isTrue();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
