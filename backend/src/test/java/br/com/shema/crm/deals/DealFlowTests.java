package br.com.shema.crm.deals;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.shema.crm.TestcontainersConfiguration;
import br.com.shema.crm.identity.domain.Role;
import br.com.shema.crm.identity.domain.User;
import br.com.shema.crm.identity.infrastructure.UserRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

/** Funil e tarefas pela API, contra Postgres real: as etapas da seção 5.5 já existem e as tarefas aparecem no card. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "shema.bootstrap-admin.email=admin@test.local",
            "shema.bootstrap-admin.password=Senha-forte-123",
            "shema.auth.login-attempts-per-minute=1000"
        })
@SuppressWarnings({"unchecked", "rawtypes"})
class DealFlowTests {

    /** Licitação do seed (SEFAZ, R$ 237.000,00). */
    private static final String SEFAZ_TENDER = "20000000-0000-4000-8000-000000000013";
    /** Órgão do seed (Nova Iguaçu). */
    private static final String NOVA_IGUACU = "10000000-0000-4000-8000-000000000017";

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

    private static List<Map> stages(Map pipeline) {
        return (List<Map>) pipeline.get("stages");
    }

    private static Map stageNamed(Map pipeline, String name) {
        return stages(pipeline).stream()
                .filter(s -> name.equals(s.get("name")))
                .findFirst()
                .orElseThrow();
    }

    private static Map deal(String title, String agencyId, String tenderId, String value, String ownerId) {
        Map body = new HashMap();
        body.put("title", title);
        body.put("agencyId", agencyId);
        body.put("tenderId", tenderId);
        body.put("value", value);
        body.put("priority", 2);
        body.put("ownerId", ownerId);
        return body;
    }

    private static Map task(String title, OffsetDateTime dueAt, String ownerId, String dealId) {
        Map body = new HashMap();
        body.put("title", title);
        body.put("dueAt", dueAt.toString());
        body.put("ownerId", ownerId);
        body.put("dealId", dealId);
        return body;
    }

    @Test
    void dealMovesThroughTheFunnelWithItsTasks() {
        String admin = token("admin@test.local", "Senha-forte-123");
        Map pipeline = ok(send(HttpMethod.GET, admin, "/api/v1/deals/pipeline", null), 200);
        assertThat(stages(pipeline))
                .extracting(s -> s.get("name"))
                .containsExactly(
                        "Sem contato",
                        "Contato feito",
                        "Identificação do interesse",
                        "Apresentação",
                        "Proposta enviada",
                        "Fechado ganho",
                        "Fechado perdido");
        String ganho = (String) stageNamed(pipeline, "Fechado ganho").get("id");

        // sem etapa: entra na primeira aberta, no fim
        Map created = ok(
                send(
                        HttpMethod.POST,
                        admin,
                        "/api/v1/deals",
                        Map.of("deal", deal("Carona teste fluxo", NOVA_IGUACU, SEFAZ_TENDER, "118500.00", null))),
                201);
        String dealId = (String) created.get("id");
        assertThat(created.get("stageName")).isEqualTo("Sem contato");
        assertThat(created.get("status")).isEqualTo("EM_ANDAMENTO");
        assertThat(created.get("isNew")).isEqualTo(true);
        assertThat(((Map) created.get("agency")).get("name")).isEqualTo("Prefeitura Municipal de Nova Iguaçu");
        assertThat(((Map) created.get("tender")).get("agencyName")).isEqualTo("SEFAZ · Secretaria de Fazenda");

        // tarefa atrasada aparece na faixa do card; a lista de tarefas mostra a negociação e o valor
        Map late = ok(
                send(
                        HttpMethod.POST,
                        admin,
                        "/api/v1/tasks",
                        task("Ligar para compras (teste)", OffsetDateTime.now().minusDays(1), null, dealId)),
                201);
        assertThat(late.get("status")).isEqualTo("ATRASADA");
        assertThat(((Map) late.get("deal")).get("title")).isEqualTo("Carona teste fluxo");
        assertThat(new BigDecimal(late.get("value").toString())).isEqualByComparingTo("118500");
        ok(
                send(
                        HttpMethod.POST,
                        admin,
                        "/api/v1/tasks",
                        task("Enviar a ata (teste)", OffsetDateTime.now().plusDays(2), null, dealId)),
                201);

        Map filtered = ok(send(HttpMethod.GET, admin, "/api/v1/deals/pipeline?q=fluxo", null), 200);
        assertThat(filtered.get("dealCount")).isEqualTo(1);
        Map semContato = stageNamed(filtered, "Sem contato");
        assertThat(semContato.get("dealCount")).isEqualTo(1);
        assertThat(new BigDecimal(semContato.get("totalValue").toString())).isEqualByComparingTo("118500");
        Map card = ((List<Map>) semContato.get("deals")).getFirst();
        assertThat(card.get("openTasks")).isEqualTo(2);
        assertThat(((Map) card.get("overdueTask")).get("title")).isEqualTo("Ligar para compras (teste)");

        Map tasksOfDeal = ok(send(HttpMethod.GET, admin, "/api/v1/tasks?dealId=" + dealId, null), 200);
        assertThat((List<Map>) tasksOfDeal.get("items"))
                .extracting(t -> t.get("title"))
                .containsExactly("Ligar para compras (teste)", "Enviar a ata (teste)");

        // mover não muda a versão; fechar como ganha registra a data
        Map moved = ok(
                send(
                        HttpMethod.POST,
                        admin,
                        "/api/v1/deals/" + dealId + "/move",
                        Map.of("stageId", ganho, "position", 0)),
                200);
        assertThat(moved.get("status")).isEqualTo("GANHA");
        assertThat(moved.get("closedAt")).isNotNull();
        assertThat(moved.get("version")).isEqualTo(created.get("version"));
        Map wonOnly = ok(send(HttpMethod.GET, admin, "/api/v1/deals/pipeline?status=GANHA", null), 200);
        assertThat(stageNamed(wonOnly, "Sem contato").get("dealCount")).isEqualTo(0);
        assertThat((List<Map>) stageNamed(wonOnly, "Fechado ganho").get("deals"))
                .extracting(d -> d.get("title"))
                .contains("Carona teste fluxo");

        // edição com versão antiga: conflito
        ok(
                send(
                        HttpMethod.PUT,
                        admin,
                        "/api/v1/deals/" + dealId,
                        Map.of(
                                "version",
                                created.get("version"),
                                "deal",
                                deal("Carona teste fluxo (ganha)", NOVA_IGUACU, SEFAZ_TENDER, "120000.00", null))),
                200);
        ResponseEntity<Map> stale = send(
                HttpMethod.PUT,
                admin,
                "/api/v1/deals/" + dealId,
                Map.of("version", created.get("version"), "deal", deal("Outro", NOVA_IGUACU, null, "1.00", null)));
        assertThat(stale.getStatusCode().value()).isEqualTo(409);

        // com tarefa aberta não apaga; conclui em massa e apaga
        Map current = ok(send(HttpMethod.GET, admin, "/api/v1/deals/" + dealId, null), 200);
        ResponseEntity<Map> blocked =
                send(HttpMethod.DELETE, admin, "/api/v1/deals/" + dealId + "?version=" + current.get("version"), null);
        assertThat(blocked.getStatusCode().value()).isEqualTo(422);
        assertThat(blocked.getBody()).containsEntry("rule", "DEAL_HAS_OPEN_TASKS");

        List<String> ids = ((List<Map>) tasksOfDeal.get("items"))
                .stream().map(t -> (String) t.get("id")).toList();
        Map done = ok(send(HttpMethod.POST, admin, "/api/v1/tasks/complete", Map.of("ids", ids)), 200);
        assertThat(done.get("completed")).isEqualTo(2);
        Map again = ok(send(HttpMethod.POST, admin, "/api/v1/tasks/complete", Map.of("ids", ids)), 200);
        assertThat(again.get("completed")).isEqualTo(0);

        ResponseEntity<Map> deleted =
                send(HttpMethod.DELETE, admin, "/api/v1/deals/" + dealId + "?version=" + current.get("version"), null);
        assertThat(deleted.getStatusCode().value()).isEqualTo(204);

        // as concluídas ficam, sem a negociação
        Map orphan = ok(send(HttpMethod.GET, admin, "/api/v1/tasks/" + ids.getFirst(), null), 200);
        assertThat(orphan.get("status")).isEqualTo("CONCLUIDA");
        assertThat(orphan.get("deal")).isNull();
    }

    @Test
    void salesChangeOnlyTheirOwnAndViewersNothing() {
        String admin = token("admin@test.local", "Senha-forte-123");
        String sales = userToken("Vendedora", "vendedora.funil@test.local", Role.SALES);
        String other = userToken("Outro vendedor", "outro.funil@test.local", Role.SALES);
        String viewer = userToken("Leitor", "leitor.funil@test.local", Role.VIEWER);
        String salesId = users.findByEmail("vendedora.funil@test.local")
                .orElseThrow()
                .getId()
                .toString();

        Map mine = ok(
                send(
                        HttpMethod.POST,
                        sales,
                        "/api/v1/deals",
                        Map.of("deal", deal("Negociação da vendedora", NOVA_IGUACU, null, "5000.00", salesId))),
                201);
        assertThat(mine.get("canEdit")).isEqualTo(true);
        String dealId = (String) mine.get("id");

        Map pipeline = ok(send(HttpMethod.GET, other, "/api/v1/deals/pipeline?mine=true", null), 200);
        assertThat(pipeline.get("dealCount")).isEqualTo(0);
        assertThat(pipeline.get("canEditStages")).isEqualTo(false);
        String contato = (String) stageNamed(pipeline, "Contato feito").get("id");

        ResponseEntity<Map> notHis = send(
                HttpMethod.POST, other, "/api/v1/deals/" + dealId + "/move", Map.of("stageId", contato, "position", 0));
        assertThat(notHis.getStatusCode().value()).isEqualTo(403);
        ok(
                send(
                        HttpMethod.POST,
                        sales,
                        "/api/v1/deals/" + dealId + "/move",
                        Map.of("stageId", contato, "position", 0)),
                200);

        // conclusão em massa é tudo ou nada
        Map own = ok(
                send(
                        HttpMethod.POST,
                        other,
                        "/api/v1/tasks",
                        task("Tarefa do outro", OffsetDateTime.now().plusDays(1), null, null)),
                201);
        Map hers = ok(
                send(
                        HttpMethod.POST,
                        sales,
                        "/api/v1/tasks",
                        task("Tarefa da vendedora", OffsetDateTime.now().plusDays(1), salesId, dealId)),
                201);
        ResponseEntity<Map> mixed = send(
                HttpMethod.POST,
                other,
                "/api/v1/tasks/complete",
                Map.of("ids", List.of(own.get("id"), hers.get("id"))));
        assertThat(mixed.getStatusCode().value()).isEqualTo(403);
        Map stillOpen = ok(send(HttpMethod.GET, other, "/api/v1/tasks/" + own.get("id"), null), 200);
        assertThat(stillOpen.get("status")).isEqualTo("ABERTA");

        Map summary = ok(send(HttpMethod.GET, sales, "/api/v1/tasks/summary?mine=true", null), 200);
        assertThat(summary.get("open")).isEqualTo(1);

        // leitor só consulta; etapas só o admin
        assertThat(send(HttpMethod.GET, viewer, "/api/v1/tasks", null)
                        .getStatusCode()
                        .value())
                .isEqualTo(200);
        assertThat(send(
                                HttpMethod.POST,
                                viewer,
                                "/api/v1/deals",
                                Map.of("deal", deal("Do leitor", NOVA_IGUACU, null, "1.00", null)))
                        .getStatusCode()
                        .value())
                .isEqualTo(403);
        assertThat(send(
                                HttpMethod.POST,
                                sales,
                                "/api/v1/deals/stages",
                                Map.of("version", pipeline.get("version"), "name", "Negociação"))
                        .getStatusCode()
                        .value())
                .isEqualTo(403);

        Map withStage = ok(
                send(
                        HttpMethod.POST,
                        admin,
                        "/api/v1/deals/stages",
                        Map.of("version", pipeline.get("version"), "name", "Etapa de teste")),
                200);
        Map added = stageNamed(withStage, "Etapa de teste");
        assertThat(added.get("kind")).isEqualTo("ABERTA");
        Map removed = ok(
                send(
                        HttpMethod.DELETE,
                        admin,
                        "/api/v1/deals/stages/" + added.get("id") + "?version=" + withStage.get("version"),
                        null),
                200);
        assertThat(stages(removed)).extracting(s -> s.get("name")).doesNotContain("Etapa de teste");
        Map ganho = stageNamed(removed, "Fechado ganho");
        ResponseEntity<Map> lastWon = send(
                HttpMethod.DELETE,
                admin,
                "/api/v1/deals/stages/" + ganho.get("id") + "?version=" + removed.get("version"),
                null);
        assertThat(lastWon.getStatusCode().value()).isEqualTo(422);
    }
}
