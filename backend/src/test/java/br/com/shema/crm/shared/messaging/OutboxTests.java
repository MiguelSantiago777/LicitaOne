package br.com.shema.crm.shared.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.shema.crm.TestcontainersConfiguration;
import br.com.shema.crm.contracts.ContractEvents;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Outbox de ponta a ponta: o evento gravado na transação chega ao RabbitMQ no envelope e é marcado como concluído. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "shema.bootstrap-admin.email=admin@test.local",
            "shema.bootstrap-admin.password=Senha-forte-123",
            "shema.auth.login-attempts-per-minute=1000"
        })
@SuppressWarnings({"unchecked", "rawtypes"})
class OutboxTests {

    /** Licitação vencida do seed (Prefeitura de Mesquita), sem contrato. */
    private static final String MESQUITA_TENDER = "20000000-0000-4000-8000-000000000008";

    @LocalServerPort
    int port;

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    AmqpAdmin amqpAdmin;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JsonMapper json;

    RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {})
                .build();
    }

    @Test
    void signedContractReachesRabbitInTheEnvelope() throws Exception {
        Queue probe = new Queue("test.contract-events." + UUID.randomUUID(), false, true, true);
        amqpAdmin.declareQueue(probe);
        amqpAdmin.declareBinding(BindingBuilder.bind(probe)
                .to(new TopicExchange(ContractEvents.EXCHANGE))
                .with("contract.#"));

        String token = (String) http.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "admin@test.local", "password", "Senha-forte-123"))
                .retrieve()
                .toEntity(Map.class)
                .getBody()
                .get("accessToken");
        Map contract = http.post()
                .uri("/api/v1/tenders/" + MESQUITA_TENDER + "/contracts")
                .header("Authorization", "Bearer " + token)
                .header("X-Correlation-Id", "teste-outbox-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("contract", Map.of("kind", "CONTRATO", "signedAt", "2026-10-21", "value", 310000)))
                .retrieve()
                .toEntity(Map.class)
                .getBody();

        Message message = rabbit.receive(probe.getName(), 15_000);
        assertThat(message).as("mensagem do contrato no RabbitMQ").isNotNull();
        JsonNode envelope = json.readTree(message.getBody());
        assertThat(envelope.get("type").asString()).isEqualTo("contract.signed");
        assertThat(envelope.get("version").asInt()).isEqualTo(1);
        assertThat(envelope.get("correlationId").asString()).isEqualTo("teste-outbox-0001");
        assertThat(envelope.get("eventId").asString()).isNotBlank();
        assertThat(envelope.get("payload").get("contractId").asString()).isEqualTo(contract.get("id"));
        assertThat(envelope.get("payload").has("metadata")).isFalse();

        // a publicação é marcada como concluída depois da confirmação
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        long pending;
        do {
            pending = jdbc.queryForObject(
                    "select count(*) from event_publication where completion_date is null"
                            + " and serialized_event like ?",
                    Long.class,
                    "%" + contract.get("id") + "%");
        } while (pending > 0 && Instant.now().isBefore(deadline));
        assertThat(pending).isZero();
    }
}
