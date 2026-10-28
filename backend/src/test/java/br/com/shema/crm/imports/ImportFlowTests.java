package br.com.shema.crm.imports;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.shema.crm.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** Importação de ponta a ponta: API → outbox → RabbitMQ → consumidor → contrato, relatório e DLQ. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "shema.bootstrap-admin.email=admin@test.local",
            "shema.bootstrap-admin.password=Senha-forte-123",
            "shema.auth.login-attempts-per-minute=1000",
            "shema.storage.local-dir=target/test-files"
        })
@SuppressWarnings({"unchecked", "rawtypes"})
class ImportFlowTests {

    @LocalServerPort
    int port;

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    AmqpAdmin amqpAdmin;

    RestClient http;
    String token;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {})
                .build();
        token = (String) http.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "admin@test.local", "password", "Senha-forte-123"))
                .retrieve()
                .toEntity(Map.class)
                .getBody()
                .get("accessToken");
    }

    private ResponseEntity<Map> post(String uri, Object body, String idempotencyKey) {
        var request =
                http.post().uri(uri).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        return request.body(body).retrieve().toEntity(Map.class);
    }

    private <T> T get(String uri, Class<T> type) {
        return http.get()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toEntity(type)
                .getBody();
    }

    private ResponseEntity<Map> upload(String contractId, byte[] xlsx) {
        return upload(contractId, "PLANILHA DE CONTROLE.xlsx", xlsx);
    }

    private ResponseEntity<Map> upload(String contractId, String fileName, byte[] content) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        return http.post()
                .uri("/api/v1/imports?contractId=" + contractId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .toEntity(Map.class);
    }

    /** Licitação vencida com um contrato de R$ 237.000,00 sem itens, como a SEFAZ antes da importação. */
    private String contract() {
        String agencyId = (String) post(
                        "/api/v1/agencies",
                        Map.of(
                                "name",
                                "SEFAZ teste " + UUID.randomUUID(),
                                "sphere",
                                "ESTADUAL",
                                "city",
                                "Rio de Janeiro",
                                "state",
                                "RJ"),
                        null)
                .getBody()
                .get("id");
        String tenderId = (String) post(
                        "/api/v1/tenders",
                        Map.of(
                                "initialStatus",
                                "VENCIDA",
                                "wonAt",
                                "2026-02-03",
                                "tender",
                                Map.of(
                                        "agencyId", agencyId,
                                        "uasg", "926201",
                                        "object", "Material gráfico diverso",
                                        "materialType", "Material gráfico",
                                        "totalValue", 237000)),
                        null)
                .getBody()
                .get("id");
        return (String) post(
                        "/api/v1/tenders/" + tenderId + "/contracts",
                        Map.of("contract", Map.of("signedAt", "2026-03-01", "value", 237000)),
                        null)
                .getBody()
                .get("id");
    }

    private Map waitUntilFinished(String jobId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        Map job;
        do {
            job = get("/api/v1/imports/" + jobId, Map.class);
        } while (!List.of("DONE", "FAILED").contains(job.get("status"))
                && Instant.now().isBefore(deadline));
        return job;
    }

    private static long waitFor(Supplier<Long> count, long atLeast) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        long value;
        do {
            value = count.get();
        } while (value < atLeast && Instant.now().isBefore(deadline));
        return value;
    }

    private Map previewSuggested(Map uploaded) {
        String jobId = (String) ((Map) uploaded.get("job")).get("id");
        Map sheet = ((List<Map>) uploaded.get("sheets"))
                .stream()
                        .filter(s -> s.get("name").equals(uploaded.get("suggestedSheet")))
                        .findFirst()
                        .orElseThrow();
        ResponseEntity<Map> preview = post(
                "/api/v1/imports/" + jobId + "/preview",
                Map.of(
                        "sheet", sheet.get("name"),
                        "headerRow", sheet.get("headerRow"),
                        "mapping", sheet.get("suggestedMapping")),
                null);
        assertThat(preview.getStatusCode().value()).isEqualTo(200);
        return preview.getBody();
    }

    @Test
    void importsItemsAndOrdersAsyncAndReimportingChangesNothing() {
        String contractId = contract();

        ResponseEntity<Map> uploaded = upload(contractId, SefazSheet.xlsx(true));
        assertThat(uploaded.getStatusCode().value()).isEqualTo(201);
        assertThat(uploaded.getBody().get("suggestedSheet")).isEqualTo("SEFAZ - CONTRATO");
        String jobId = (String) ((Map) uploaded.getBody().get("job")).get("id");

        Map preview = previewSuggested(uploaded.getBody());
        assertThat((Map<String, Number>) preview.get("totals"))
                .containsEntry("CREATED", 5)
                .containsEntry("ERROR", 1);
        // 1.600 cartões × 0,20 + 24 adesivos × 18 + 1.200 canetas × 4,10
        assertThat(new BigDecimal(preview.get("newOrderValue").toString())).isEqualByComparingTo("5672.00");

        String key = "confirma-" + jobId;
        Map<String, Object> confirm =
                Map.of("orderDate", LocalDate.now().minusDays(1).toString());
        ResponseEntity<Map> accepted = post("/api/v1/imports/" + jobId + "/confirm", confirm, key);
        assertThat(accepted.getStatusCode().value()).isEqualTo(202);
        assertThat(accepted.getHeaders().getLocation()).hasToString("/api/v1/imports/" + jobId);
        assertThat(post("/api/v1/imports/" + jobId + "/confirm", confirm, key)
                        .getStatusCode()
                        .value())
                .isEqualTo(202);

        Map job = waitUntilFinished(jobId);
        assertThat(job.get("status")).isEqualTo("DONE");
        assertThat(job)
                .containsEntry("totalRows", 6)
                .containsEntry("importedRows", 5)
                .containsEntry("errorRows", 1);
        assertThat(new BigDecimal(job.get("orderValue").toString())).isEqualByComparingTo("5672.00");

        Map contract = get("/api/v1/contracts/" + contractId, Map.class);
        assertThat((List<?>) contract.get("items")).hasSize(5);
        assertThat((List<?>) contract.get("orders")).hasSize(1);
        assertThat(new BigDecimal(contract.get("ordered").toString())).isEqualByComparingTo("5672.00");
        assertThat(get("/api/v1/tenders/" + contract.get("tenderId"), Map.class))
                .containsEntry("status", "EM_ANDAMENTO");

        String report = new String(
                http.get()
                        .uri("/api/v1/imports/" + jobId + "/report")
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .body(byte[].class),
                StandardCharsets.UTF_8);
        assertThat(report).startsWith("﻿Linha;Situação;");
        assertThat(report).contains("11;Erro;;CARTAZ A2 594X420MM").contains("setenta e dois");
        assertThat(report).contains("6;Item criado;;CARTÕES DE VISITA 0.09X0.05 CM;4800;0,2;1600;");

        // a mesma planilha de novo: nada muda, nenhum pedido novo
        Map again = upload(contractId, SefazSheet.xlsx(true)).getBody();
        Map preview2 = previewSuggested(again);
        assertThat((Map<String, Number>) preview2.get("totals")).containsEntry("UNCHANGED", 5);
        assertThat(new BigDecimal(preview2.get("newOrderValue").toString())).isEqualByComparingTo("0");
        String job2 = (String) ((Map) again.get("job")).get("id");
        post("/api/v1/imports/" + job2 + "/confirm", confirm, null);
        assertThat(waitUntilFinished(job2)).containsEntry("status", "DONE");
        assertThat((List<?>) get("/api/v1/contracts/" + contractId, Map.class).get("orders"))
                .hasSize(1);

        assertThat(get("/api/v1/imports?contractId=" + contractId, List.class)).hasSize(2);
    }

    @Test
    void previewNeedsTheRequiredColumnsAndConfirmNeedsTheDate() {
        String contractId = contract();
        Map uploaded = upload(contractId, SefazSheet.xlsx(false)).getBody();
        String jobId = (String) ((Map) uploaded.get("job")).get("id");

        ResponseEntity<Map> incomplete = post(
                "/api/v1/imports/" + jobId + "/preview",
                Map.of("sheet", "SEFAZ - CONTRATO", "headerRow", 5, "mapping", Map.of("DESCRIPTION", 2)),
                null);
        assertThat(incomplete.getStatusCode().value()).isEqualTo(422);
        assertThat((String) incomplete.getBody().get("detail"))
                .contains("Quantidade")
                .contains("Valor unitário");

        // confirmar antes da prévia não vale
        ResponseEntity<Map> early = post(
                "/api/v1/imports/" + jobId + "/confirm",
                Map.of("orderDate", LocalDate.now().toString()),
                null);
        assertThat(early.getStatusCode().value()).isEqualTo(422);

        ResponseEntity<Map> wrongFile = upload(contractId, "contrato.pdf", "%PDF".getBytes(StandardCharsets.UTF_8));
        assertThat(wrongFile.getStatusCode().value()).isEqualTo(422);
        assertThat(wrongFile.getBody()).containsEntry("rule", "FILE_TYPE");
    }

    @Test
    void unreadableMessageGoesToTheDlqAndCanBeReplayed() {
        String dlq = ImportRequested.QUEUE + ".dlq";
        rabbit.send(
                "",
                ImportRequested.QUEUE,
                new Message("isto não é json".getBytes(StandardCharsets.UTF_8), new MessageProperties()));

        long stuck = waitFor(
                () -> {
                    Object count = amqpAdmin.getQueueProperties(dlq).get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
                    return count == null ? 0L : ((Number) count).longValue();
                },
                1);
        assertThat(stuck).isGreaterThanOrEqualTo(1);

        List<Map> queues = get("/api/v1/admin/dead-letters", List.class);
        assertThat(queues)
                .filteredOn(q -> q.get("queue").equals(ImportRequested.QUEUE))
                .singleElement()
                .satisfies(q ->
                        assertThat(((Number) q.get("messages")).longValue()).isGreaterThanOrEqualTo(1));

        ResponseEntity<Map> replay =
                post("/api/v1/admin/dead-letters/" + ImportRequested.QUEUE + "/replay", Map.of(), null);
        assertThat(replay.getStatusCode().value()).isEqualTo(200);
        assertThat(((Number) replay.getBody().get("replayed")).intValue()).isGreaterThanOrEqualTo(1);
    }
}
