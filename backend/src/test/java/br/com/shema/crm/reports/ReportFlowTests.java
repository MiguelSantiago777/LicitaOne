package br.com.shema.crm.reports;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.shema.crm.TestcontainersConfiguration;
import br.com.shema.crm.identity.domain.Role;
import br.com.shema.crm.identity.domain.User;
import br.com.shema.crm.identity.infrastructure.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

/**
 * Relatórios pela API, contra Postgres com o seed: cada número confere com a soma feita direto no banco, os filtros
 * reduzem o total, o PDF sai e o perfil Leitura vê.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "shema.bootstrap-admin.email=admin@test.local",
            "shema.bootstrap-admin.password=Senha-forte-123",
            "shema.auth.login-attempts-per-minute=1000"
        })
@SuppressWarnings({"unchecked", "rawtypes"})
class ReportFlowTests {

    @LocalServerPort
    int port;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

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

    private ResponseEntity<Map> get(String token, String uri) {
        return http.get()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toEntity(Map.class);
    }

    private Map ok(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode().value())
                .as("%s", response.getBody())
                .isEqualTo(200);
        return response.getBody();
    }

    private static BigDecimal money(Object value) {
        return new BigDecimal(String.valueOf(value));
    }

    /** O total de cada ano na resposta, somado de "years". */
    private static BigDecimal yearTotal(Map report, int year) {
        return ((List<Map>) report.get("years"))
                .stream()
                        .filter(y -> ((Number) y.get("year")).intValue() == year)
                        .map(y -> money(y.get("total")))
                        .findFirst()
                        .orElse(BigDecimal.ZERO);
    }

    private BigDecimal sql(String query, int year) {
        BigDecimal v = jdbc.queryForObject(query, BigDecimal.class, year);
        return v == null ? BigDecimal.ZERO : v;
    }

    @Test
    void eachMetricMatchesTheDatabase() {
        String admin = token("admin@test.local", "Senha-forte-123");
        int year = LocalDate.now().getYear();
        Map<String, String> sums = Map.of(
                "PEDIDOS",
                "select sum(value) from contract_orders where extract(year from ordered_at) = ?",
                "CONTRATOS",
                "select sum(value) from contracts where extract(year from signed_at) = ?",
                "LICITACOES",
                "select sum(total_value) from tenders where won_at is not null"
                        + " and status not in ('EM_DISPUTA', 'PERDIDA') and extract(year from won_at) = ?",
                "CARONAS",
                "select sum(value) from caronas where extract(year from adhered_at) = ?");
        for (var entry : sums.entrySet()) {
            for (int y : new int[] {year, year - 1}) {
                Map report = ok(get(admin, "/api/v1/reports?metric=" + entry.getKey() + "&year=" + y));
                BigDecimal expected = sql(entry.getValue(), y);
                Map summary = (Map) report.get("summary");
                assertThat(money(summary.get("total")))
                        .as("%s em %d", entry.getKey(), y)
                        .isEqualByComparingTo(expected);
                assertThat(yearTotal(report, y)).isEqualByComparingTo(expected);
                BigDecimal months = ((List<Map>) report.get("months"))
                        .stream().map(m -> money(m.get("value"))).reduce(BigDecimal.ZERO, BigDecimal::add);
                assertThat(months).isEqualByComparingTo(expected);
                BigDecimal byState = ((List<Map>) report.get("byState"))
                        .stream().map(s -> money(s.get("value"))).reduce(BigDecimal.ZERO, BigDecimal::add);
                assertThat(byState).isEqualByComparingTo(expected);
            }
        }
    }

    @Test
    void filtersNarrowTheTotal() {
        String admin = token("admin@test.local", "Senha-forte-123");
        int year = LocalDate.now().getYear();
        Map all = ok(get(admin, "/api/v1/reports?metric=PEDIDOS&year=" + year));
        Map rj = ok(get(admin, "/api/v1/reports?metric=PEDIDOS&year=" + year + "&state=rj&sphere=municipal"));
        assertThat(rj.get("state")).isEqualTo("RJ");
        assertThat(rj.get("sphereLabel")).isEqualTo("Municipal");
        BigDecimal expected = jdbc.queryForObject("""
                select coalesce(sum(o.value), 0) from contract_orders o
                  join contracts c on c.id = o.contract_id
                  join tenders t on t.id = c.tender_id
                  join agencies a on a.id = t.agency_id
                 where extract(year from o.ordered_at) = ? and a.state = 'RJ' and a.sphere = 'MUNICIPAL'
                """, BigDecimal.class, year);
        assertThat(money(((Map) rj.get("summary")).get("total"))).isEqualByComparingTo(expected);
        assertThat(money(((Map) rj.get("summary")).get("total")))
                .isLessThanOrEqualTo(money(((Map) all.get("summary")).get("total")));

        assertThat(get(admin, "/api/v1/reports?metric=PEDIDOS&year=1990")
                        .getStatusCode()
                        .value())
                .isEqualTo(400);
        assertThat(get(admin, "/api/v1/reports?metric=PEDIDOS&sphere=GALACTICA")
                        .getStatusCode()
                        .value())
                .isEqualTo(400);
        assertThat(get(admin, "/api/v1/reports?metric=LUCRO").getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    void readOnlyProfileSeesAndDownloadsThePdf() {
        if (users.findByEmail("leitura.relatorios@test.local").isEmpty()) {
            users.save(new User(
                    "Leitura",
                    "leitura.relatorios@test.local",
                    passwordEncoder.encode("Senha-teste-123"),
                    Role.VIEWER));
        }
        String viewer = token("leitura.relatorios@test.local", "Senha-teste-123");
        ok(get(viewer, "/api/v1/reports?metric=CARONAS"));
        int year = LocalDate.now().getYear();
        ResponseEntity<byte[]> pdf = http.get()
                .uri("/api/v1/reports/pdf?metric=LICITACOES&year=" + year + "&state=RJ")
                .header("Authorization", "Bearer " + viewer)
                .retrieve()
                .toEntity(byte[].class);
        assertThat(pdf.getStatusCode().value()).isEqualTo(200);
        assertThat(new String(pdf.getBody(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("Relatorio-licitacoes-vencidas-" + year + "-RJ.pdf");
    }
}
