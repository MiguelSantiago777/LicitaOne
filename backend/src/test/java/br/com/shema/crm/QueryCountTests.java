package br.com.shema.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Performance das listagens (Fase 6): uma página de 25 linhas não faz mais consultas que uma de 3. Se alguém
 * buscar o responsável, o órgão ou a negociação linha a linha (N+1), o número cresce e o teste quebra. As telas sem
 * paginação têm um teto de consultas.
 */
@Import({TestcontainersConfiguration.class, QueryCounter.Config.class})
@SpringBootTest(
        properties = {
            "shema.bootstrap-admin.email=admin@test.local",
            "shema.bootstrap-admin.password=Senha-forte-123",
            "shema.auth.login-attempts-per-minute=1000",
            "shema.storage.local-dir=target/test-files",
            "shema.alerts.cron=-",
            "shema.alerts.mail-enabled=false"
        })
@AutoConfigureMockMvc
class QueryCountTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    String token;

    @BeforeEach
    void login() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@test.local\",\"password\":\"Senha-forte-123\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        token = (String) json.readValue(body, Map.class).get("accessToken");
    }

    private Map<?, ?> call(String uri) throws Exception {
        String body = mvc.perform(get(uri).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.startsWith("{") ? json.readValue(body, Map.class) : Map.of();
    }

    private int statements(String uri) throws Exception {
        QueryCounter.start();
        try {
            call(uri);
        } catch (Exception | AssertionError e) {
            QueryCounter.stop();
            throw e;
        }
        int count = QueryCounter.stop();
        return count;
    }

    @Test
    void pagedListsDoNotGrowWithThePageSize() throws Exception {
        mvc.perform(post("/api/v1/alerts/run").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        for (String uri : new String[] {
            "/api/v1/tenders?size=%d",
            "/api/v1/tenders?status=EM_ANDAMENTO&size=%d",
            "/api/v1/tasks?situation=TODAS&size=%d",
            "/api/v1/agencies?size=%d",
            "/api/v1/alerts?size=%d"
        }) {
            Map<?, ?> page = call(uri.formatted(25));
            Object total = page.get("totalItems");
            assertThat(total == null ? 0 : ((Number) total).longValue())
                    .as("o seed precisa de mais de 3 linhas em %s", uri)
                    .isGreaterThan(3);
            int few = statements(uri.formatted(3));
            int many = statements(uri.formatted(25));
            // Com 25 linhas pode ser até uma a menos: a página incompleta dispensa o count
            assertThat(many)
                    .as("consultas de %s com 25 linhas (com 3 foram %d)", uri, few)
                    .isLessThanOrEqualTo(few);
        }
    }

    @Test
    void screensWithoutPagingStayUnderACeiling() throws Exception {
        Map<String, Integer> ceilings = Map.of(
                "/api/v1/deals/pipeline", 8,
                "/api/v1/price-records?filter=TODAS", 5,
                "/api/v1/price-records/prospects?state=RJ", 3,
                "/api/v1/tenders/summary", 2,
                "/api/v1/tenders/top-agencies", 2,
                "/api/v1/contracts/without-orders", 3,
                "/api/v1/tenders/20000000-0000-4000-8000-000000000013/contracts", 7,
                "/api/v1/tenders/20000000-0000-4000-8000-000000000013/history", 3,
                "/api/v1/boards", 3,
                "/api/v1/users", 3);
        for (var entry : ceilings.entrySet()) {
            assertThat(statements(entry.getKey()))
                    .as("consultas de %s", entry.getKey())
                    .isLessThanOrEqualTo(entry.getValue());
        }
    }
}
