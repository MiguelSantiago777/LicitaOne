package br.com.shema.crm.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.shema.crm.TestcontainersConfiguration;
import br.com.shema.crm.extraction.application.DocumentReader;
import br.com.shema.crm.extraction.domain.RawReading;
import br.com.shema.crm.extraction.domain.RawReading.RawField;
import br.com.shema.crm.extraction.domain.RawReading.RawItem;
import br.com.shema.crm.identity.domain.Role;
import br.com.shema.crm.identity.domain.User;
import br.com.shema.crm.identity.infrastructure.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Leitura com IA pela API, com fila e banco reais e um leitor falso no lugar do Claude (sem rede e sem chave): pedido
 * (202) → consumidor → resultado conferido; permissões; tipos recusados; falha do leitor.
 */
@Import({TestcontainersConfiguration.class, ExtractionFlowTests.FakeReaderConfig.class})
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "shema.bootstrap-admin.email=admin@test.local",
            "shema.bootstrap-admin.password=Senha-forte-123",
            "shema.auth.login-attempts-per-minute=1000"
        })
@SuppressWarnings({"unchecked", "rawtypes"})
class ExtractionFlowTests {

    /** Conceição de Macabu (vencida, do administrador). */
    private static final String TENDER = "20000000-0000-4000-8000-000000000009";

    private static final byte[] PDF = "%PDF-1.7 ata para ler".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    /** Lê sempre a mesma ata; um arquivo com "recusada" no nome simula a IA recusando o documento. */
    @TestConfiguration(proxyBeanMethods = false)
    static class FakeReaderConfig {

        @Bean
        @Primary
        DocumentReader fakeDocumentReader() {
            return new DocumentReader() {
                @Override
                public boolean enabled() {
                    return true;
                }

                @Override
                public Reading read(ReadRequest request) {
                    if (request.fileName().contains("recusada")) {
                        throw new ReadingFailed("A IA não aceitou ler este documento.", "falso", 100L, 0L, null);
                    }
                    RawReading raw = new RawReading(
                            "ATA",
                            new RawField("012/2026", 1),
                            new RawField("2026-05-16", 1),
                            new RawField("", 0),
                            new RawField("2027-05-15", 2),
                            new RawField("SIM", 3),
                            new RawField("97500.00", 2),
                            new RawField("Prefeitura Municipal de Conceição de Macabu", 1),
                            new RawField("Shema Suprimentos e Serviços Ltda", 1),
                            List.of(
                                    new RawItem("1", "Banner em lona 440g", "M²", "1500", "45.00", 4),
                                    new RawItem("1", "Adesivo perfurado", "M²", "1000", "30.00", 4)),
                            List.of());
                    return new Reading(raw, "leitor-falso", 1200, 340);
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

    private String admin() {
        return token("admin@test.local", "Senha-forte-123");
    }

    private String userToken(String email, Role role) {
        if (users.findByEmail(email).isEmpty()) {
            users.save(new User("Teste", email, passwordEncoder.encode("Senha-teste-123"), role));
        }
        return token(email, "Senha-teste-123");
    }

    private ResponseEntity<Map> send(HttpMethod method, String token, String uri) {
        return http.method(method)
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toEntity(Map.class);
    }

    private String document(String token, String kind, String fileName, byte[] content) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        ResponseEntity<Map> response = http.post()
                .uri("/api/v1/documents?tenderId=" + TENDER + "&kind=" + kind + "&title=" + fileName)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .toEntity(Map.class);
        assertThat(response.getStatusCode().value())
                .as("%s", response.getBody())
                .isEqualTo(201);
        return (String) response.getBody().get("id");
    }

    private Map waitUntilFinished(String token, String extractionId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        Map extraction;
        do {
            extraction = send(HttpMethod.GET, token, "/api/v1/extractions/" + extractionId)
                    .getBody();
        } while (!List.of("DONE", "FAILED").contains(extraction.get("status"))
                && Instant.now().isBefore(deadline));
        return extraction;
    }

    @Test
    void readsTheAtaInTheBackgroundAndReturnsACheckedSuggestion() {
        String admin = admin();
        assertThat(send(HttpMethod.GET, admin, "/api/v1/extractions/settings").getBody())
                .containsEntry("enabled", true);

        String docId = document(admin, "ATA", "ata-012-2026.pdf", PDF);
        assertThat(send(HttpMethod.GET, admin, "/api/v1/documents/" + docId + "/extractions/latest")
                        .getStatusCode()
                        .value())
                .isEqualTo(204);

        ResponseEntity<Map> requested = send(HttpMethod.POST, admin, "/api/v1/documents/" + docId + "/extractions");
        assertThat(requested.getStatusCode().value())
                .as("%s", requested.getBody())
                .isEqualTo(202);
        assertThat(requested.getBody()).containsEntry("status", "QUEUED").containsEntry("kind", "ATA");
        String extractionId = (String) requested.getBody().get("id");

        Map done = waitUntilFinished(admin, extractionId);
        assertThat(done).containsEntry("status", "DONE").containsEntry("model", "leitor-falso");
        assertThat(((Number) done.get("inputTokens")).longValue()).isEqualTo(1200);
        Map result = (Map) done.get("result");
        assertThat(result).containsEntry("documentType", "ATA");
        assertThat((Map) result.get("number"))
                .containsEntry("value", "012/2026")
                .containsEntry("page", 1);
        assertThat((Map) result.get("validUntil")).containsEntry("value", "2027-05-15");
        assertThat((Map) result.get("allowsAdhesion")).containsEntry("value", true);
        List<Map> items = (List<Map>) result.get("items");
        assertThat(items).hasSize(2);
        assertThat(items.getFirst()).containsEntry("description", "Banner em lona 440g");
        assertThat(((Number) items.getFirst().get("unitPrice")).doubleValue()).isEqualTo(45.0);
        assertThat((List) result.get("warnings")).isEmpty();

        assertThat(send(HttpMethod.GET, admin, "/api/v1/documents/" + docId + "/extractions/latest")
                        .getBody())
                .containsEntry("id", extractionId);

        // a pessoa confere e salva pelo formulário de sempre; o histórico guarda que veio da IA
        ResponseEntity<Map> ata = http.post()
                .uri("/api/v1/price-records")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "tenderId",
                        TENDER,
                        "record",
                        Map.of(
                                "number", "012/2026",
                                "signedAt", "2026-05-16",
                                "validUntil", "2027-05-15",
                                "allowsAdhesion", true,
                                "documentId", docId),
                        "suggestedByAi",
                        true))
                .retrieve()
                .toEntity(Map.class);
        assertThat(ata.getStatusCode().value()).as("%s", ata.getBody()).isEqualTo(201);
        List<Map> history = http.get()
                .uri("/api/v1/price-records/history?tenderId=" + TENDER)
                .header("Authorization", "Bearer " + admin)
                .retrieve()
                .body(List.class);
        assertThat(history)
                .filteredOn(e -> "ATA_CREATED".equals(e.get("type")))
                .singleElement()
                .satisfies(e -> assertThat((String) e.get("summary"))
                        .endsWith("dados sugeridos pela IA e conferidos por Administrador"));
    }

    @Test
    void aRefusedReadingEndsWithTheReasonAndNoRetry() {
        String admin = admin();
        String docId = document(admin, "ATA", "ata-recusada.pdf", PDF);

        String extractionId = (String) send(HttpMethod.POST, admin, "/api/v1/documents/" + docId + "/extractions")
                .getBody()
                .get("id");
        Map failed = waitUntilFinished(admin, extractionId);

        assertThat(failed)
                .containsEntry("status", "FAILED")
                .containsEntry("errorMessage", "A IA não aceitou ler este documento.")
                .containsEntry("result", null);
    }

    @Test
    void onlyPdfAtasAndContractsAndOnlyWhoCanEditTheTender() {
        String admin = admin();
        String viewer = userToken("leitor.ia@test.local", Role.VIEWER);

        String edital = document(admin, "EDITAL", "edital.pdf", PDF);
        ResponseEntity<Map> notSupported = send(HttpMethod.POST, admin, "/api/v1/documents/" + edital + "/extractions");
        assertThat(notSupported.getStatusCode().value()).isEqualTo(422);
        assertThat(notSupported.getBody()).containsEntry("rule", "AI_KIND_NOT_SUPPORTED");

        String photo = document(admin, "ATA", "ata.png", PNG);
        ResponseEntity<Map> notPdf = send(HttpMethod.POST, admin, "/api/v1/documents/" + photo + "/extractions");
        assertThat(notPdf.getStatusCode().value()).isEqualTo(422);
        assertThat(notPdf.getBody()).containsEntry("rule", "AI_PDF_ONLY");

        String ata = document(admin, "ATA", "ata-leitor.pdf", PDF);
        assertThat(send(HttpMethod.POST, viewer, "/api/v1/documents/" + ata + "/extractions")
                        .getStatusCode()
                        .value())
                .isEqualTo(403);
        assertThat(send(HttpMethod.GET, viewer, "/api/v1/extractions/settings")
                        .getStatusCode()
                        .value())
                .isEqualTo(200);
    }
}
