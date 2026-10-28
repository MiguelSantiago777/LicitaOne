package br.com.shema.crm.boards;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.shema.crm.TestcontainersConfiguration;
import br.com.shema.crm.identity.domain.Role;
import br.com.shema.crm.identity.domain.User;
import br.com.shema.crm.identity.infrastructure.UserRepository;
import java.math.BigDecimal;
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

/** Quadros pela API, contra Postgres real: os dois quadros do cliente já existem e começam vazios. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "shema.bootstrap-admin.email=admin@test.local",
            "shema.bootstrap-admin.password=Senha-forte-123",
            "shema.auth.login-attempts-per-minute=1000"
        })
@SuppressWarnings({"unchecked", "rawtypes"})
class BoardFlowTests {

    /** Licitação do seed (SEFAZ, R$ 237.000,00). */
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

    private ResponseEntity<Map> send(HttpMethod method, String token, String uri, Object body) {
        var request = http.method(method).uri(uri).header("Authorization", "Bearer " + token);
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return request.retrieve().toEntity(Map.class);
    }

    /** POST de card: confere o 201 e devolve o quadro (o corpo aparece na mensagem se falhar). */
    private Map created(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode().value())
                .as("%s", response.getBody())
                .isEqualTo(201);
        return response.getBody();
    }

    private static List<Map> columns(Map board) {
        return (List<Map>) board.get("columns");
    }

    private static List<String> titles(Map column) {
        return ((List<Map>) column.get("cards"))
                .stream().map(c -> (String) c.get("title")).toList();
    }

    @Test
    void clientBoardsExistAndTheTeamBuildsThem() {
        String token = token("admin@test.local", "Senha-forte-123");
        List<Map> boards = http.get()
                .uri("/api/v1/boards")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(List.class);
        assertThat(boards).extracting(b -> b.get("name")).contains("Organização mensal", "Licitações vencidas");

        Map created = send(HttpMethod.POST, token, "/api/v1/boards", Map.of("name", "Quadro de teste"))
                .getBody();
        String id = (String) created.get("id");
        assertThat(columns(created)).isEmpty();

        Map board = created;
        for (String name : List.of("Novembro", "Dezembro", "Feito")) {
            board = send(
                            HttpMethod.POST,
                            token,
                            "/api/v1/boards/" + id + "/columns",
                            Map.of("version", board.get("version"), "name", name))
                    .getBody();
        }
        assertThat(columns(board)).extracting(c -> c.get("name")).containsExactly("Novembro", "Dezembro", "Feito");
        String nov = (String) columns(board).get(0).get("id");
        String dez = (String) columns(board).get(1).get("id");

        // versão antiga: conflito
        ResponseEntity<Map> stale = send(
                HttpMethod.POST, token, "/api/v1/boards/" + id + "/columns", Map.of("version", 0, "name", "Outra"));
        assertThat(stale.getStatusCode().value()).isEqualTo(409);

        board = created(send(
                HttpMethod.POST,
                token,
                "/api/v1/boards/" + id + "/cards",
                Map.of("columnId", nov, "card", Map.of("title", "Sessão SEFAZ", "tenderId", SEFAZ_TENDER))));
        board = created(send(
                HttpMethod.POST,
                token,
                "/api/v1/boards/" + id + "/cards",
                Map.of("columnId", nov, "card", Map.of("title", "Ligar para Magé", "dueDate", "2026-01-10"))));
        Map novColumn = columns(board).get(0);
        assertThat(titles(novColumn)).containsExactly("Sessão SEFAZ", "Ligar para Magé");
        assertThat(new BigDecimal(novColumn.get("totalValue").toString())).isEqualByComparingTo("237000");
        Map sefazCard = ((List<Map>) novColumn.get("cards")).get(0);
        assertThat(((Map) sefazCard.get("tender")).get("agencyName")).isEqualTo("SEFAZ · Secretaria de Fazenda");
        Map late = ((List<Map>) novColumn.get("cards")).get(1);
        assertThat(late.get("overdue")).isEqualTo(true);

        // arrasta o primeiro para dezembro e depois volta para o topo de novembro, abaixo de nada
        board = send(
                        HttpMethod.POST,
                        token,
                        "/api/v1/cards/" + sefazCard.get("id") + "/move",
                        Map.of("columnId", dez, "position", 0))
                .getBody();
        assertThat(titles(columns(board).get(0))).containsExactly("Ligar para Magé");
        assertThat(titles(columns(board).get(1))).containsExactly("Sessão SEFAZ");
        assertThat(((List<Map>) columns(board).get(0).get("cards")).get(0).get("position"))
                .isEqualTo(0);

        board = send(
                        HttpMethod.POST,
                        token,
                        "/api/v1/cards/" + sefazCard.get("id") + "/move",
                        Map.of("columnId", nov, "position", 0))
                .getBody();
        assertThat(titles(columns(board).get(0))).containsExactly("Sessão SEFAZ", "Ligar para Magé");

        // coluna com card não sai
        ResponseEntity<Map> notEmpty = send(
                HttpMethod.DELETE,
                token,
                "/api/v1/boards/" + id + "/columns/" + nov + "?version=" + board.get("version"),
                null);
        assertThat(notEmpty.getStatusCode().value()).isEqualTo(422);
        assertThat(notEmpty.getBody()).containsEntry("rule", "COLUMN_NOT_EMPTY");

        // editar com versão antiga do card: conflito; a mudança de coluna não conta como edição
        ResponseEntity<Map> edited = send(
                HttpMethod.PUT,
                token,
                "/api/v1/cards/" + sefazCard.get("id"),
                Map.of("version", sefazCard.get("version"), "card", Map.of("title", "Sessão SEFAZ – proposta pronta")));
        assertThat(edited.getStatusCode().value()).isEqualTo(200);
        ResponseEntity<Map> staleCard = send(
                HttpMethod.PUT,
                token,
                "/api/v1/cards/" + sefazCard.get("id"),
                Map.of("version", sefazCard.get("version"), "card", Map.of("title", "Outro título")));
        assertThat(staleCard.getStatusCode().value()).isEqualTo(409);

        board = send(HttpMethod.DELETE, token, "/api/v1/cards/" + late.get("id"), null)
                .getBody();
        assertThat(titles(columns(board).get(0))).containsExactly("Sessão SEFAZ – proposta pronta");
    }

    @Test
    void viewerSeesButNeverChangesBoards() {
        if (users.findByEmail("leitor.quadros@test.local").isEmpty()) {
            users.save(new User(
                    "Leitor", "leitor.quadros@test.local", passwordEncoder.encode("Senha-leitor-1"), Role.VIEWER));
        }
        String viewer = token("leitor.quadros@test.local", "Senha-leitor-1");
        Map board = http.get()
                .uri("/api/v1/boards/40000000-0000-4000-8000-000000000001")
                .header("Authorization", "Bearer " + viewer)
                .retrieve()
                .body(Map.class);
        assertThat(board.get("name")).isEqualTo("Organização mensal");
        assertThat(board.get("canEdit")).isEqualTo(false);
        assertThat(send(HttpMethod.POST, viewer, "/api/v1/boards", Map.of("name", "X"))
                        .getStatusCode()
                        .value())
                .isEqualTo(403);
    }
}
