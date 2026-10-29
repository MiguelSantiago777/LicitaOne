package br.com.shema.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/**
 * O perfil de produção sobe de verdade (Fase 6): sem os dados de exemplo, sem Swagger, com o cookie de sessão
 * {@code Secure} e com a chave JWT vinda da configuração.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("prod")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "shema.bootstrap-admin.email=admin@test.local",
            "shema.bootstrap-admin.password=Senha-forte-123",
            "shema.storage.local-dir=target/test-files",
            "shema.alerts.cron=-",
            "shema.jwt.key-id=prod-test"
        })
class ProdProfileTests {

    @DynamicPropertySource
    static void signingKey(DynamicPropertyRegistry registry) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes())
                        .encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        registry.add("shema.jwt.private-key", () -> pem);
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void productionHasNoSampleDataNoSwaggerAndASecureSessionCookie() {
        assertThat(jdbc.queryForObject("select count(*) from tenders", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from flyway_schema_history where script like 'R__dev_seed%'", Long.class))
                .isZero();

        RestClient http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {})
                .build();
        assertThat(http.get()
                        .uri("/v3/api-docs")
                        .retrieve()
                        .toBodilessEntity()
                        .getStatusCode()
                        .value())
                .isIn(401, 404);

        ResponseEntity<Map> login = http.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "admin@test.local", "password", "Senha-forte-123"))
                .retrieve()
                .toEntity(Map.class);
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        assertThat(login.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict");
    }
}
