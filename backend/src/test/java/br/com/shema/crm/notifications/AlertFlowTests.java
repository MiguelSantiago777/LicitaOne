package br.com.shema.crm.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.shema.crm.TestcontainersConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Alertas de ponta a ponta: verificar → outbox → RabbitMQ → tarefa com rascunho e aviso no Mailpit; sem duplicata
 * na segunda verificação; tarefa concluída sozinha quando o fato deixa de valer; SMTP fora → DLQ → reprocessar.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "shema.bootstrap-admin.email=admin@test.local",
            "shema.bootstrap-admin.password=Senha-forte-123",
            "shema.auth.login-attempts-per-minute=1000",
            "shema.storage.local-dir=target/test-files",
            "shema.alerts.cron=-",
            "shema.alerts.tender-without-contract-days=20",
            "spring.mail.properties.mail.smtp.connectiontimeout=1000",
            "spring.mail.properties.mail.smtp.timeout=1000",
            "spring.mail.properties.mail.smtp.writetimeout=1000"
        })
@SuppressWarnings({"unchecked", "rawtypes"})
class AlertFlowTests {

    /** O mesmo Mailpit do compose: SMTP na 1025 e a API que o teste usa para ler os e-mails na 8025. */
    static final GenericContainer<?> MAILPIT =
            new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.32")).withExposedPorts(1025, 8025);

    static {
        MAILPIT.start();
    }

    @DynamicPropertySource
    static void mail(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
    }

    @LocalServerPort
    int port;

    @Autowired
    MeterRegistry meters;

    RestClient http;
    RestClient mailpit;
    String token;
    String adminId;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {})
                .build();
        mailpit = RestClient.builder()
                .baseUrl("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025))
                .build();
        Map login = http.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "admin@test.local", "password", "Senha-forte-123"))
                .retrieve()
                .toEntity(Map.class)
                .getBody();
        token = (String) login.get("accessToken");
        adminId = (String) ((Map) login.get("user")).get("id");
    }

    private ResponseEntity<Map> send(String method, String uri, Object body) {
        var request = http.method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(uri)
                .header("Authorization", "Bearer " + token);
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return request.retrieve().toEntity(Map.class);
    }

    private ResponseEntity<Map> post(String uri, Object body) {
        return send("POST", uri, body);
    }

    private <T> T get(String uri, Class<T> type) {
        return http.get()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toEntity(type)
                .getBody();
    }

    /** Espera o estado final do fluxo assíncrono (nunca um sleep fixo). */
    private static <T> T await(Supplier<T> probe, Duration timeout) {
        Instant limit = Instant.now().plus(timeout);
        while (true) {
            T value = probe.get();
            if (value != null) {
                return value;
            }
            if (Instant.now().isAfter(limit)) {
                throw new AssertionError("O estado esperado não chegou em " + timeout);
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    /** Órgão novo, com um contato com e-mail, e uma licitação vencida há tantos dias, do administrador. */
    private String wonTender(String agencyName, int wonDaysAgo) {
        Map agency = post(
                        "/api/v1/agencies",
                        Map.of("name", agencyName, "sphere", "MUNICIPAL", "city", "Itaguaí", "state", "RJ"))
                .getBody();
        send(
                "PUT",
                "/api/v1/agencies/" + agency.get("id") + "/contacts",
                Map.of(
                        "version",
                        agency.get("version"),
                        "contacts",
                        List.of(Map.of("name", "Setor de compras", "email", "compras@itaguai.rj.gov.br"))));
        Map<String, Object> tender = new HashMap<>(Map.of(
                "agencyId",
                agency.get("id"),
                "processNumber",
                "77/2026",
                "object",
                "Material gráfico",
                "materialType",
                "Material gráfico",
                "totalValue",
                50000.00,
                "ownerId",
                adminId));
        return (String) post(
                        "/api/v1/tenders",
                        Map.of(
                                "initialStatus",
                                "VENCIDA",
                                "wonAt",
                                LocalDate.now().minusDays(wonDaysAgo).toString(),
                                "tender",
                                tender))
                .getBody()
                .get("id");
    }

    private Map sign(String tenderId, int signedDaysAgo) {
        ResponseEntity<Map> signed = post(
                "/api/v1/tenders/" + tenderId + "/contracts",
                Map.of(
                        "contract",
                        Map.of(
                                "kind",
                                "CONTRATO",
                                "number",
                                "55/2026",
                                "signedAt",
                                LocalDate.now().minusDays(signedDaysAgo).toString(),
                                "value",
                                50000.00),
                        "items",
                        List.of(Map.of("description", "Banner", "quantity", 500, "unitPrice", 100))));
        assertThat(signed.getStatusCode().value()).isEqualTo(201);
        return signed.getBody();
    }

    private Map runAlerts() {
        ResponseEntity<Map> run = post("/api/v1/alerts/run", null);
        assertThat(run.getStatusCode().value()).isEqualTo(200);
        return run.getBody();
    }

    private List<Map> alertTasks(String tenderId) {
        Map page = get("/api/v1/tasks?situation=TODAS&tenderId=" + tenderId, Map.class);
        return ((List<Map>) page.get("items"))
                .stream().filter(t -> "ALERTA".equals(t.get("origin"))).toList();
    }

    private Map awaitTask(String tenderId, String titleStart) {
        return await(
                () -> alertTasks(tenderId).stream()
                        .filter(t -> ((String) t.get("title")).startsWith(titleStart))
                        .findFirst()
                        .orElse(null),
                Duration.ofSeconds(20));
    }

    private Map awaitMail(String subjectPart) {
        return await(
                () -> {
                    Map messages =
                            mailpit.get().uri("/api/v1/messages").retrieve().body(Map.class);
                    return ((List<Map>) messages.get("messages"))
                            .stream()
                                    .filter(m -> ((String) m.get("Subject")).contains(subjectPart))
                                    .findFirst()
                                    .orElse(null);
                },
                Duration.ofSeconds(20));
    }

    private long deadLetters(String queue) {
        List<Map> queues = get("/api/v1/admin/dead-letters", List.class);
        return queues.stream()
                .filter(q -> q.get("queue").equals(queue))
                .mapToLong(q -> ((Number) q.get("messages")).longValue())
                .sum();
    }

    @Test
    void contractWithoutOrderCreatesOneTaskWithDraftAndNoticeAndClosesWhenTheOrderArrives() {
        String agency = "Prefeitura de Itaguaí " + UUID.randomUUID();
        String tenderId = wonTender(agency, 60);
        Map contract = sign(tenderId, 40);

        Map run = runAlerts();
        assertThat(((Number) run.get("raised")).intValue()).isGreaterThanOrEqualTo(1);

        Map task = awaitTask(tenderId, "Sem pedido há 40 dias");
        assertThat(task.get("status")).isEqualTo("ABERTA");
        assertThat(((Map) task.get("owner")).get("id")).isEqualTo(adminId);
        Map draft = (Map) task.get("emailDraft");
        assertThat(draft.get("to")).isEqualTo("compras@itaguai.rj.gov.br");
        assertThat((String) draft.get("subject")).startsWith("Previsão de pedidos: Contrato nº 55/2026");
        assertThat((String) draft.get("body")).contains("Visto que participamos do certame");

        // Aviso interno para o responsável, com o saldo a pedir (ADR 0006)
        Map mail = awaitMail("Contrato sem pedido há 40 dias: " + agency);
        assertThat(((List<Map>) mail.get("To")).getFirst().get("Address")).isEqualTo("admin@test.local");
        Map alert = await(
                () -> ((List<Map>) get("/api/v1/alerts?rule=CONTRATO_SEM_PEDIDO&size=100", Map.class)
                                .get("items"))
                        .stream()
                                .filter(a -> tenderId.equals(a.get("tenderId")) && a.get("notifiedAt") != null)
                                .findFirst()
                                .orElse(null),
                Duration.ofSeconds(20));
        assertThat(alert.get("taskId")).isEqualTo(task.get("id"));
        assertThat(alert.get("notifiedTo")).isEqualTo("admin@test.local");

        // Verificar de novo não repete a tarefa nem o e-mail
        runAlerts();
        assertThat(alertTasks(tenderId)).hasSize(1);

        // Tarefa de alerta não pode ser apagada
        ResponseEntity<Map> delete =
                send("DELETE", "/api/v1/tasks/" + task.get("id") + "?version=" + task.get("version"), null);
        assertThat(delete.getStatusCode().value()).isEqualTo(422);

        // O pedido chega: a tarefa se conclui sozinha
        List<Map> items = (List<Map>) contract.get("items");
        ResponseEntity<Map> ordered = post(
                "/api/v1/contracts/" + contract.get("id") + "/orders",
                Map.of(
                        "version",
                        contract.get("version"),
                        "orderedAt",
                        LocalDate.now().toString(),
                        "lines",
                        List.of(Map.of("itemId", items.getFirst().get("id"), "quantity", 10))));
        assertThat(ordered.getStatusCode().value()).isEqualTo(201);
        Map done = await(
                () -> alertTasks(tenderId).stream()
                        .filter(t -> "CONCLUIDA".equals(t.get("status")))
                        .findFirst()
                        .orElse(null),
                Duration.ofSeconds(20));
        assertThat(done.get("completedBySystem")).isEqualTo(true);
    }

    @Test
    void tenderWithoutContractIsBornOverdueAndClosesWhenTheContractIsSigned() {
        String tenderId = wonTender("Prefeitura de Mangaratiba " + UUID.randomUUID(), 25);
        runAlerts();
        Map task = awaitTask(tenderId, "Vencida há 25 dias sem contrato");
        assertThat(task.get("status")).isEqualTo("ATRASADA");
        assertThat(task.get("emailDraft")).isNull();

        sign(tenderId, 1);
        await(
                () -> alertTasks(tenderId).stream()
                        .filter(t -> "CONCLUIDA".equals(t.get("status")))
                        .findFirst()
                        .orElse(null),
                Duration.ofSeconds(20));
    }

    @Test
    void smtpDownSendsTheNoticeToTheDlqAndReplayDeliversItWithoutDuplicatingTheTask() {
        String agency = "Prefeitura de Paracambi " + UUID.randomUUID();
        String tenderId = wonTender(agency, 50);
        sign(tenderId, 35);
        long before = deadLetters("shemacrm.alerts.notify-team");

        MAILPIT.getDockerClient().pauseContainerCmd(MAILPIT.getContainerId()).exec();
        try {
            runAlerts();
            // A tarefa sai pela outra fila, mesmo com o e-mail fora
            awaitTask(tenderId, "Sem pedido há 35 dias");
            await(() -> deadLetters("shemacrm.alerts.notify-team") > before ? true : null, Duration.ofSeconds(60));
        } finally {
            MAILPIT.getDockerClient()
                    .unpauseContainerCmd(MAILPIT.getContainerId())
                    .exec();
        }

        ResponseEntity<Map> replay = post("/api/v1/admin/dead-letters/shemacrm.alerts.notify-team/replay", null);
        assertThat(((Number) replay.getBody().get("replayed")).intValue()).isGreaterThanOrEqualTo(1);
        awaitMail("Contrato sem pedido há 35 dias: " + agency);
        assertThat(alertTasks(tenderId)).hasSize(1);
        assertThat(deadLetters("shemacrm.alerts.notify-team")).isZero();
    }

    @Test
    void onlyAdminsRunTheCheckAndEveryoneSeesTheSettings() {
        runAlerts();
        Map settings = get("/api/v1/alerts/settings", Map.class);
        // A última verificação vem do lock do ShedLock, gravado com a hora UTC do banco (V15)
        assertThat(Duration.between(Instant.parse((String) settings.get("lastRunAt")), Instant.now())
                        .abs())
                .isLessThan(Duration.ofMinutes(1));
        assertThat(settings.get("contractWithoutOrderDays")).isEqualTo(30);
        assertThat(settings.get("priceRecordExpiringDays")).isEqualTo(List.of(90, 60, 30));
        assertThat(settings.get("schedule")).isEqualTo("Desligado");
        assertThat((List) settings.get("rules")).hasSize(3);

        String email = "leitura-" + UUID.randomUUID() + "@test.local";
        post(
                "/api/v1/users",
                Map.of("name", "Leitura", "email", email, "password", "Senha-forte-123", "role", "VIEWER"));
        String viewer = (String) http.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "Senha-forte-123"))
                .retrieve()
                .toEntity(Map.class)
                .getBody()
                .get("accessToken");
        ResponseEntity<Map> refused = http.post()
                .uri("/api/v1/alerts/run")
                .header("Authorization", "Bearer " + viewer)
                .retrieve()
                .toEntity(Map.class);
        assertThat(refused.getStatusCode().value()).isEqualTo(403);
        ResponseEntity<Map> list = http.get()
                .uri("/api/v1/alerts")
                .header("Authorization", "Bearer " + viewer)
                .retrieve()
                .toEntity(Map.class);
        assertThat(list.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void businessMetricsSurviveGarbageCollection() {
        String tenderId = wonTender("Prefeitura de Seropédica " + UUID.randomUUID(), 50);
        sign(tenderId, 45);
        // O Micrometer guarda a gauge por referência fraca: sem referência forte, viraria NaN aqui
        System.gc();
        for (String name : List.of(
                "shema.contracts.without.order",
                "shema.price.records.expiring",
                "shema.tasks.overdue",
                "shema.imports.failed")) {
            assertThat(meters.get(name).gauge().value()).as(name).isNotNaN();
        }
        assertThat(meters.get("shema.contracts.without.order").gauge().value()).isGreaterThanOrEqualTo(1);
        assertThat(meters.get("shema.messaging.dlq.messages")
                        .tag("queue", "shemacrm.alerts.notify-team")
                        .gauge()
                        .value())
                .isNotNaN();
    }
}
