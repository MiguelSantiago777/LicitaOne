package br.com.shema.crm.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.shema.crm.TestcontainersConfiguration;
import br.com.shema.crm.identity.domain.Role;
import br.com.shema.crm.identity.domain.User;
import br.com.shema.crm.identity.infrastructure.UserRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/** Fornecedores e produtos pela API, contra Postgres real: nichos, menor preço, filtros, arquivos e permissões. */
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
class CatalogFlowTests {

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
            users.save(new User("Pessoa " + role, email, passwordEncoder.encode("Senha-teste-123"), role));
        }
        return token(email, "Senha-teste-123");
    }

    private ResponseEntity<Map> send(HttpMethod method, String token, String uri, Object body) {
        var request = http.method(method).uri(uri).header("Authorization", "Bearer " + token);
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return request.retrieve().toEntity(Map.class);
    }

    private Map ok(ResponseEntity<Map> response, int status) {
        assertThat(response.getStatusCode().value())
                .as("%s", response.getBody())
                .isEqualTo(status);
        return response.getBody();
    }

    private static String unique(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    private Map niche(String token, String name) {
        return ok(send(HttpMethod.POST, token, "/api/v1/catalog/niches", Map.of("name", name)), 201);
    }

    private Map supplier(String token, String name, String nicheId) {
        Map body = new HashMap();
        body.put("legalName", name);
        body.put("nicheIds", List.of(nicheId));
        body.put("contacts", List.of(Map.of("name", "Vendas", "phone", "(21) 99999-0000")));
        return ok(send(HttpMethod.POST, token, "/api/v1/catalog/suppliers", body), 201);
    }

    private static Map offer(Object supplierId, String price) {
        Map o = new HashMap();
        o.put("supplierId", supplierId);
        o.put("unitPrice", price);
        return o;
    }

    private static Map product(String name, Object nicheId, List<Map> offers) {
        Map body = new HashMap();
        body.put("name", name);
        body.put("nicheId", nicheId);
        body.put("unit", "un");
        body.put("offers", offers);
        return body;
    }

    @Test
    void nichesSuppliersAndProductsWithTheLowestPrice() {
        String admin = admin();
        String nicheName = unique("Limpeza hospitalar");
        Map niche = niche(admin, nicheName);
        String nicheId = (String) niche.get("id");

        // Mesmo nome com outro acento e outra caixa: conflito
        ok(
                send(
                        HttpMethod.POST,
                        admin,
                        "/api/v1/catalog/niches",
                        Map.of("name", nicheName.toUpperCase().replace("I", "Í"))),
                409);

        Map alfa = supplier(admin, unique("Alfa Hospitalar"), nicheId);
        Map beta = supplier(admin, unique("Beta Hospitalar"), nicheId);
        assertThat((List) alfa.get("contacts")).hasSize(1);

        Map created = ok(
                send(
                        HttpMethod.POST,
                        admin,
                        "/api/v1/catalog/products",
                        product(
                                unique("Álcool 70% 1 L"),
                                nicheId,
                                List.of(offer(alfa.get("id"), "9.90"), offer(beta.get("id"), "8.75")))),
                201);
        assertThat(created.get("lowestOfferSupplierId")).isEqualTo(beta.get("id"));
        List<Map> offers = (List<Map>) created.get("offers");
        assertThat(offers.getFirst().get("quotedOn")).isEqualTo(LocalDate.now().toString());

        // Lista filtrada pelo nicho, busca sem acento, com o menor preço
        Map page = ok(send(HttpMethod.GET, admin, "/api/v1/catalog/products?q=alcool&nicheId=" + nicheId, null), 200);
        List<Map> items = (List<Map>) page.get("items");
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().get("lowestPrice")).isEqualTo(8.75);
        assertThat((String) items.getFirst().get("lowestSupplierName")).startsWith("Beta Hospitalar");
        assertThat(items.getFirst().get("supplierCount")).isEqualTo(2);

        // Filtro por fornecedor
        Map byAlfa =
                ok(send(HttpMethod.GET, admin, "/api/v1/catalog/products?supplierId=" + alfa.get("id"), null), 200);
        assertThat((List) byAlfa.get("items")).hasSize(1);

        // No fornecedor: o produto, com o preço dele e se é o menor
        Map betaView = ok(send(HttpMethod.GET, admin, "/api/v1/catalog/suppliers/" + beta.get("id"), null), 200);
        List<Map> betaProducts = (List<Map>) betaView.get("products");
        assertThat(betaProducts).hasSize(1);
        assertThat(betaProducts.getFirst().get("lowestPrice")).isEqualTo(true);

        // Arquivar o fornecedor mais barato: o menor preço passa para o outro
        ok(
                send(
                        HttpMethod.POST,
                        admin,
                        "/api/v1/catalog/suppliers/" + beta.get("id") + "/archive",
                        Map.of("version", betaView.get("version"))),
                200);
        Map after = ok(send(HttpMethod.GET, admin, "/api/v1/catalog/products/" + created.get("id"), null), 200);
        assertThat(after.get("lowestOfferSupplierId")).isEqualTo(alfa.get("id"));

        // Fornecedor arquivado não entra como preço novo em outro produto
        ok(
                send(
                        HttpMethod.POST,
                        admin,
                        "/api/v1/catalog/products",
                        product(unique("Luva"), nicheId, List.of(offer(beta.get("id"), "1.00")))),
                422);

        // Contagem do nicho: 1 fornecedor ativo (o outro foi arquivado) e 1 produto
        List<Map> niches = http.get()
                .uri("/api/v1/catalog/niches")
                .header("Authorization", "Bearer " + admin)
                .retrieve()
                .body(List.class);
        Map counted = niches.stream()
                .filter(n -> n.get("id").equals(nicheId))
                .findFirst()
                .orElseThrow();
        assertThat(counted.get("supplierCount")).isEqualTo(1);
        assertThat(counted.get("productCount")).isEqualTo(1);
    }

    @Test
    void autosaveConflictAndArchivedNiche() {
        String admin = admin();
        Map niche = niche(admin, unique("Copa e cozinha"));
        Map product = ok(
                send(
                        HttpMethod.POST,
                        admin,
                        "/api/v1/catalog/products",
                        product(unique("Copo descartável"), niche.get("id"), List.of())),
                201);
        Map body = Map.of(
                "version", product.get("version"), "product", product("Copo 200 ml", niche.get("id"), List.of()));
        Map saved = ok(send(HttpMethod.PUT, admin, "/api/v1/catalog/products/" + product.get("id"), body), 200);
        assertThat(saved.get("name")).isEqualTo("Copo 200 ml");
        // A tela com a versão velha toma conflito
        ok(send(HttpMethod.PUT, admin, "/api/v1/catalog/products/" + product.get("id"), body), 409);

        // Nicho arquivado: o produto que já estava nele continua salvando, mas não entra produto novo
        ok(
                send(
                        HttpMethod.POST,
                        admin,
                        "/api/v1/catalog/niches/" + niche.get("id") + "/archive",
                        Map.of("version", niche.get("version"))),
                200);
        ok(
                send(
                        HttpMethod.PUT,
                        admin,
                        "/api/v1/catalog/products/" + product.get("id"),
                        Map.of(
                                "version",
                                saved.get("version"),
                                "product",
                                product("Copo 180 ml", niche.get("id"), List.of()))),
                200);
        ok(
                send(
                        HttpMethod.POST,
                        admin,
                        "/api/v1/catalog/products",
                        product(unique("Prato"), niche.get("id"), List.of())),
                422);
    }

    @Test
    void photoUploadDownloadAndDelete() {
        String admin = admin();
        Map niche = niche(admin, unique("Fotos"));
        Map product = ok(
                send(
                        HttpMethod.POST,
                        admin,
                        "/api/v1/catalog/products",
                        product(unique("Vassoura"), niche.get("id"), List.of())),
                201);
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new ByteArrayResource(png) {
            @Override
            public String getFilename() {
                return "vassoura.png";
            }
        });
        Map file = ok(
                http.post()
                        .uri("/api/v1/catalog/products/" + product.get("id") + "/files?kind=FOTO")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(form)
                        .retrieve()
                        .toEntity(Map.class),
                201);
        assertThat(file.get("contentType")).isEqualTo("image/png");

        // Enviar arquivo não muda a versão do produto
        Map reloaded = ok(send(HttpMethod.GET, admin, "/api/v1/catalog/products/" + product.get("id"), null), 200);
        assertThat(reloaded.get("version")).isEqualTo(product.get("version"));
        assertThat((List) reloaded.get("files")).hasSize(1);

        String fileUri = "/api/v1/catalog/products/" + product.get("id") + "/files/" + file.get("id");
        ResponseEntity<byte[]> download = http.get()
                .uri(fileUri)
                .header("Authorization", "Bearer " + admin)
                .retrieve()
                .toEntity(byte[].class);
        assertThat(download.getBody()).isEqualTo(png);

        // Uma ficha que diz ser PDF mas é PNG é recusada
        var fake = new LinkedMultiValueMap<String, Object>();
        fake.add("file", new ByteArrayResource(png) {
            @Override
            public String getFilename() {
                return "ficha.pdf";
            }
        });
        assertThat(http.post()
                        .uri("/api/v1/catalog/products/" + product.get("id") + "/files?kind=FICHA")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(fake)
                        .retrieve()
                        .toEntity(Map.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(422);

        ok(send(HttpMethod.DELETE, admin, fileUri, null), 204);
        ok(send(HttpMethod.GET, admin, fileUri, null), 404);
    }

    @Test
    void readOnlyProfileOnlyConsults() {
        String reader = userToken("leitura.catalogo@test.local", Role.VIEWER);
        ok(send(HttpMethod.GET, reader, "/api/v1/catalog/suppliers", null), 200);
        Map product = ok(
                send(HttpMethod.GET, reader, "/api/v1/catalog/products/63000000-0000-4000-8000-000000000001", null),
                200);
        assertThat(product.get("canEdit")).isEqualTo(false);
        ok(send(HttpMethod.POST, reader, "/api/v1/catalog/niches", Map.of("name", unique("Proibido"))), 403);

        String sales = userToken("vendas.catalogo@test.local", Role.SALES);
        niche(sales, unique("Vendas cria"));
    }
}
