package br.com.shema.crm.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.shema.crm.TestcontainersConfiguration;
import br.com.shema.crm.extraction.application.DocumentReader;
import br.com.shema.crm.extraction.domain.RawFollowUp;
import br.com.shema.crm.extraction.domain.RawResponse;
import br.com.shema.crm.identity.domain.Role;
import br.com.shema.crm.identity.domain.User;
import br.com.shema.crm.identity.infrastructure.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

/**
 * Registro de contato, resposta do órgão e follow-up (Fase 9), com banco real e um leitor falso: o contato entra na
 * lista, o follow-up recebe o contexto (saldo, último pedido, últimos contatos), e só quem pode alterar a licitação
 * registra, apaga e pede à IA.
 */
@Import({TestcontainersConfiguration.class, FollowUpFlowTests.FakeReaderConfig.class})
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "shema.bootstrap-admin.email=admin@test.local",
            "shema.bootstrap-admin.password=Senha-forte-123",
            "shema.auth.login-attempts-per-minute=1000"
        })
@SuppressWarnings({"unchecked", "rawtypes"})
class FollowUpFlowTests {

    static final AtomicReference<DocumentReader.ResponseRequest> LAST_RESPONSE = new AtomicReference<>();
    static final AtomicReference<DocumentReader.FollowUpRequest> LAST_FOLLOW_UP = new AtomicReference<>();

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeReaderConfig {

        @Bean
        @Primary
        DocumentReader fakeAssistant() {
            return new DocumentReader() {
                @Override
                public boolean enabled() {
                    return true;
                }

                @Override
                public Reading read(ReadRequest request) {
                    throw new ReadingFailed("não usado aqui");
                }

                @Override
                public Answer<RawResponse> readResponse(ResponseRequest request) {
                    LAST_RESPONSE.set(request);
                    if (request.text().contains("falhe")) {
                        throw new ReadingFailed("A IA está ocupada ou fora do ar agora.");
                    }
                    return new Answer<>(
                            new RawResponse(
                                    "PEDIDO_PREVISTO",
                                    "2027-03-01",
                                    false,
                                    "Pedem em março.",
                                    "Cobrar o pedido de março",
                                    "2027-02-25",
                                    List.of()),
                            "leitor-falso",
                            300,
                            80);
                }

                @Override
                public Answer<RawFollowUp> writeFollowUp(FollowUpRequest request) {
                    LAST_FOLLOW_UP.set(request);
                    return new Answer<>(
                            new RawFollowUp("Pedidos de março", "Prezados(as), conforme conversamos...", List.of()),
                            "leitor-falso",
                            500,
                            120);
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

    private ResponseEntity<Map> send(HttpMethod method, String token, String uri, Object body) {
        RestClient.RequestBodySpec request = http.method(method).uri(uri).header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return request.retrieve().toEntity(Map.class);
    }

    private List<Map> list(String token, String uri) {
        return http.get()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(List.class);
    }

    /** Licitação vencida com um contrato de dois itens e um pedido. Devolve [licitação, contrato]. */
    private String[] tenderWithContract(String token) {
        String agencyId = (String) send(
                        HttpMethod.POST,
                        token,
                        "/api/v1/agencies",
                        Map.of(
                                "name", "Prefeitura de Magé " + UUID.randomUUID(),
                                "sphere", "MUNICIPAL",
                                "city", "Magé",
                                "state", "RJ"))
                .getBody()
                .get("id");
        String tenderId = (String) send(
                        HttpMethod.POST,
                        token,
                        "/api/v1/tenders",
                        Map.of(
                                "initialStatus",
                                "VENCIDA",
                                "wonAt",
                                "2026-04-10",
                                "tender",
                                Map.of(
                                        "agencyId", agencyId,
                                        "processNumber", "12/2026",
                                        "object", "Material de expediente",
                                        "materialType", "Expediente",
                                        "totalValue", 300000.00)))
                .getBody()
                .get("id");
        Map contract = send(
                        HttpMethod.POST,
                        token,
                        "/api/v1/tenders/" + tenderId + "/contracts",
                        Map.of(
                                "contract",
                                Map.of(
                                        "kind", "CONTRATO",
                                        "number", "12/2026",
                                        "signedAt", "2026-05-16",
                                        "value", 300000.00),
                                "items",
                                List.of(
                                        Map.of("description", "Resma de papel A4", "quantity", 2000, "unitPrice", 100),
                                        Map.of("description", "Caneta azul", "quantity", 4000, "unitPrice", 25))))
                .getBody();
        List<Map> items = (List<Map>) contract.get("items");
        ResponseEntity<Map> ordered = send(
                HttpMethod.POST,
                token,
                "/api/v1/contracts/" + contract.get("id") + "/orders",
                Map.of(
                        "version",
                        contract.get("version"),
                        "orderedAt",
                        "2026-06-01",
                        "lines",
                        List.of(Map.of("itemId", items.get(0).get("id"), "quantity", 500))));
        assertThat(ordered.getStatusCode().value()).as("%s", ordered.getBody()).isEqualTo(201);
        return new String[] {tenderId, (String) contract.get("id")};
    }

    @Test
    void registersContactsAndTheFollowUpUsesThemWithTheContractBalance() {
        String admin = admin();
        String[] ids = tenderWithContract(admin);
        String tenderId = ids[0];

        ResponseEntity<Map> created = send(
                HttpMethod.POST,
                admin,
                "/api/v1/tenders/" + tenderId + "/contacts",
                Map.of(
                        "contractId",
                        ids[1],
                        "channel",
                        "WHATSAPP",
                        "outcome",
                        "PEDIDO_PREVISTO",
                        "contactedAt",
                        LocalDate.now().toString(),
                        "summary",
                        "Pedem em março, depois do orçamento.",
                        "suggestedByAi",
                        true));
        assertThat(created.getStatusCode().value()).as("%s", created.getBody()).isEqualTo(201);
        assertThat(created.getBody())
                .containsEntry("channelLabel", "WhatsApp")
                .containsEntry("outcomeLabel", "Pedido previsto")
                .containsEntry("suggestedByAi", true)
                .containsEntry("canRemove", true);

        assertThat(list(admin, "/api/v1/tenders/" + tenderId + "/contacts"))
                .singleElement()
                .satisfies(c -> assertThat(c).containsEntry("summary", "Pedem em março, depois do orçamento."));

        ResponseEntity<Map> draft = send(
                HttpMethod.POST,
                admin,
                "/api/v1/tenders/" + tenderId + "/follow-up-draft",
                Map.of("channel", "EMAIL", "tone", "FORMAL"));
        assertThat(draft.getStatusCode().value()).as("%s", draft.getBody()).isEqualTo(200);
        assertThat((Map) draft.getBody().get("draft")).containsEntry("subject", "Pedidos de março");
        DocumentReader.FollowUpRequest sent = LAST_FOLLOW_UP.get();
        assertThat(sent.whatsapp()).isFalse();
        assertThat(sent.tone()).isEqualTo("FORMAL");
        assertThat(sent.signature()).isEqualTo("Administrador");
        assertThat(sent.context())
                .contains("Contrato nº 12/2026")
                .contains("saldo a pedir R$ 250.000,00")
                .contains("Último pedido em 01/06/2026")
                .contains("Caneta azul (4.000)")
                .contains("WhatsApp (Pedido previsto): Pedem em março");
    }

    @Test
    void interpretsTheAgencyResponseWithoutSavingAnything() {
        String admin = admin();
        String tenderId = tenderWithContract(admin)[0];

        ResponseEntity<Map> read = send(
                HttpMethod.POST,
                admin,
                "/api/v1/tenders/" + tenderId + "/contacts/interpretation",
                Map.of(
                        "text", "Bom dia, o empenho deve sair em março. Att., Maria (21) 99999-0000",
                        "contactedAt", LocalDate.now().toString(),
                        "channel", "WhatsApp"));
        assertThat(read.getStatusCode().value()).as("%s", read.getBody()).isEqualTo(200);
        Map result = (Map) read.getBody().get("result");
        assertThat(result)
                .containsEntry("outcome", "PEDIDO_PREVISTO")
                .containsEntry("expectedDate", "2027-03-01")
                .containsEntry("taskTitle", "Cobrar o pedido de março");
        assertThat(LAST_RESPONSE.get().context()).contains("Contato de").contains("por WhatsApp");
        assertThat(list(admin, "/api/v1/tenders/" + tenderId + "/contacts")).isEmpty();

        ResponseEntity<Map> failed = send(
                HttpMethod.POST,
                admin,
                "/api/v1/tenders/" + tenderId + "/contacts/interpretation",
                Map.of("text", "por favor falhe agora"));
        assertThat(failed.getStatusCode().value()).isEqualTo(422);
        assertThat(failed.getBody()).containsEntry("rule", "AI_FAILED");
    }

    @Test
    void onlyWhoCanEditRegistersAndOnlyTheAuthorOrAnAdminRemoves() {
        String admin = admin();
        String tenderId = tenderWithContract(admin)[0];
        String viewer = userToken("leitor.contato@test.local", Role.VIEWER);
        Map body = Map.of(
                "channel",
                "TELEFONE",
                "contactedAt",
                LocalDate.now().toString(),
                "summary",
                "Ligaram pedindo certidão.");

        assertThat(send(HttpMethod.POST, viewer, "/api/v1/tenders/" + tenderId + "/contacts", body)
                        .getStatusCode()
                        .value())
                .isEqualTo(403);
        assertThat(send(
                                HttpMethod.POST,
                                viewer,
                                "/api/v1/tenders/" + tenderId + "/follow-up-draft",
                                Map.of("channel", "WHATSAPP"))
                        .getStatusCode()
                        .value())
                .isEqualTo(403);

        ResponseEntity<Map> future = send(
                HttpMethod.POST,
                admin,
                "/api/v1/tenders/" + tenderId + "/contacts",
                Map.of(
                        "channel",
                        "EMAIL",
                        "contactedAt",
                        LocalDate.now().plusDays(2).toString(),
                        "summary",
                        "x"));
        assertThat(future.getBody()).containsEntry("rule", "CONTACT_DATE_IN_FUTURE");

        String contactId = (String) send(HttpMethod.POST, admin, "/api/v1/tenders/" + tenderId + "/contacts", body)
                .getBody()
                .get("id");
        assertThat(list(viewer, "/api/v1/tenders/" + tenderId + "/contacts"))
                .singleElement()
                .satisfies(c -> assertThat(c).containsEntry("canRemove", false));
        assertThat(send(HttpMethod.DELETE, admin, "/api/v1/tenders/" + tenderId + "/contacts/" + contactId, null)
                        .getStatusCode()
                        .value())
                .isEqualTo(204);
        assertThat(list(admin, "/api/v1/tenders/" + tenderId + "/contacts")).isEmpty();
    }
}
