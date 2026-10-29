package br.com.shema.crm.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.shema.crm.TestcontainersConfiguration;
import br.com.shema.crm.identity.domain.Role;
import br.com.shema.crm.identity.domain.User;
import br.com.shema.crm.identity.infrastructure.UserRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

/** Fluxo de login de ponta a ponta contra Postgres e RabbitMQ reais (Testcontainers). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "shema.bootstrap-admin.email=admin@test.local",
            "shema.bootstrap-admin.password=Senha-forte-123",
            "shema.auth.login-attempts-per-minute=1000",
            "shema.auth.max-failed-attempts=3",
            "shema.auth.refresh-reuse-grace=0s"
        })
class AuthFlowTests {

    @LocalServerPort
    int port;

    RestClient http;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {})
                .build();
    }

    @Test
    void loginReturnsAccessTokenAndHttpOnlyRefreshCookie() {
        ResponseEntity<Map> response = login("admin@test.local", "Senha-forte-123");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsKey("accessToken").containsEntry("tokenType", "Bearer");
        assertThat(((Map<?, ?>) response.getBody().get("user")).get("role")).isEqualTo("ADMIN");
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).startsWith("shema_refresh=").contains("HttpOnly", "SameSite=Strict", "Path=/api/v1/auth");
        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isNotBlank();
    }

    @Test
    void wrongPasswordReturnsStableErrorCode() {
        ResponseEntity<Map> response = login("ninguem@test.local", "errada");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody())
                .containsEntry("code", "INVALID_CREDENTIALS")
                .containsKey("correlationId");
    }

    @Test
    void invalidBodyReturnsFieldErrors() {
        ResponseEntity<Map> response = http.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "nao-e-email", "password", ""))
                .retrieve()
                .toEntity(Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("code", "VALIDATION_ERROR");
        assertThat((List<?>) response.getBody().get("errors")).hasSize(2);
    }

    @Test
    void protectedEndpointRequiresToken() {
        ResponseEntity<Map> response = http.get().uri("/api/v1/me").retrieve().toEntity(Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).containsEntry("code", "UNAUTHENTICATED");
    }

    @Test
    void meAndAdminEndpointsWithToken() {
        String token =
                (String) login("admin@test.local", "Senha-forte-123").getBody().get("accessToken");

        ResponseEntity<Map> me = http.get()
                .uri("/api/v1/me")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toEntity(Map.class);
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        assertThat(me.getBody()).containsEntry("email", "admin@test.local").containsEntry("initials", "A");

        ResponseEntity<List> users = http.get()
                .uri("/api/v1/users")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toEntity(List.class);
        assertThat(users.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void refreshRotatesTokenAndDetectsReuse() {
        String firstCookie = cookieValue(login("admin@test.local", "Senha-forte-123"));

        ResponseEntity<Map> refreshed = refresh(firstCookie);
        assertThat(refreshed.getStatusCode().value()).isEqualTo(200);
        String secondCookie = cookieValue(refreshed);
        assertThat(secondCookie).isNotEqualTo(firstCookie);

        // Reapresentar o token já usado derruba todas as sessões, inclusive a nova
        assertThat(refresh(firstCookie).getBody()).containsEntry("code", "INVALID_REFRESH_TOKEN");
        assertThat(refresh(secondCookie).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void refreshAfterLogoutIsInvalidButDoesNotKillOtherSessions() {
        String sessionA = cookieValue(login("admin@test.local", "Senha-forte-123"));
        String sessionB = cookieValue(login("admin@test.local", "Senha-forte-123"));
        http.post()
                .uri("/api/v1/auth/logout")
                .header(HttpHeaders.COOKIE, "shema_refresh=" + sessionA)
                .retrieve()
                .toBodilessEntity();

        assertThat(refresh(sessionA).getBody()).containsEntry("code", "INVALID_REFRESH_TOKEN");
        assertThat(refresh(sessionB).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void locksAccountAfterRepeatedFailures() {
        users.save(
                new User("Conta Bloqueio", "bloqueio@test.local", passwordEncoder.encode("Senha-certa-1"), Role.SALES));

        assertThat(login("bloqueio@test.local", "errada").getBody()).containsEntry("code", "INVALID_CREDENTIALS");
        assertThat(login("bloqueio@test.local", "errada").getBody()).containsEntry("code", "INVALID_CREDENTIALS");
        assertThat(login("bloqueio@test.local", "errada").getStatusCode().value())
                .isEqualTo(423);
        // Bloqueada: nem a senha certa entra
        assertThat(login("bloqueio@test.local", "Senha-certa-1").getBody()).containsEntry("code", "ACCOUNT_LOCKED");
    }

    @Test
    void concurrentLoginsOfSameUserDoNotConflict() throws Exception {
        users.save(new User("Dois Logins", "dois@test.local", passwordEncoder.encode("Senha-dois-1"), Role.SALES));
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var results = executor.invokeAll(java.util.Collections.nCopies(
                    6,
                    () -> login("dois@test.local", "Senha-dois-1")
                            .getStatusCode()
                            .value()));
            for (var result : results) {
                assertThat(result.get()).isEqualTo(200);
            }
        }
    }

    @Test
    void viewerCannotListUsers() {
        users.save(new User("Leitor", "leitor@test.local", passwordEncoder.encode("Senha-leitor-1"), Role.VIEWER));
        String token =
                (String) login("leitor@test.local", "Senha-leitor-1").getBody().get("accessToken");

        ResponseEntity<Map> response = http.get()
                .uri("/api/v1/users")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toEntity(Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).containsEntry("code", "ACCESS_DENIED");
    }

    @Test
    void weakPasswordsAreRefusedWithTheReason() {
        String token =
                (String) login("admin@test.local", "Senha-forte-123").getBody().get("accessToken");
        for (var weak : Map.of(
                        "curta-1234", "pelo menos 12 caracteres",
                        "Senha1234567", "muito comum",
                        "maria.lima-2026", "não pode conter o seu e-mail")
                .entrySet()) {
            ResponseEntity<Map> response = http.post()
                    .uri("/api/v1/users")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "name", "Maria Lima",
                            "email", "maria.lima@test.local",
                            "role", "SALES",
                            "password", weak.getKey()))
                    .retrieve()
                    .toEntity(Map.class);
            assertThat(response.getStatusCode().value()).as(weak.getKey()).isEqualTo(400);
            assertThat((String) response.getBody().get("detail")).contains(weak.getValue());
        }
    }

    private ResponseEntity<Map> login(String email, String password) {
        return http.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .retrieve()
                .toEntity(Map.class);
    }

    private ResponseEntity<Map> refresh(String cookieValue) {
        return http.post()
                .uri("/api/v1/auth/refresh")
                .header(HttpHeaders.COOKIE, "shema_refresh=" + cookieValue)
                .retrieve()
                .toEntity(Map.class);
    }

    private static String cookieValue(ResponseEntity<?> response) {
        String header = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        return header.substring("shema_refresh=".length(), header.indexOf(';'));
    }
}
