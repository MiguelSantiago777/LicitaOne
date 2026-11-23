package br.com.shema.crm.catalog.api;

import br.com.shema.crm.catalog.application.NicheService;
import br.com.shema.crm.catalog.application.NicheService.NicheView;
import br.com.shema.crm.catalog.application.ProductService;
import br.com.shema.crm.catalog.application.ProductService.FileView;
import br.com.shema.crm.catalog.application.ProductService.ProductSummary;
import br.com.shema.crm.catalog.application.ProductService.ProductView;
import br.com.shema.crm.catalog.application.SupplierService;
import br.com.shema.crm.catalog.application.SupplierService.SupplierOption;
import br.com.shema.crm.catalog.application.SupplierService.SupplierSummary;
import br.com.shema.crm.catalog.application.SupplierService.SupplierView;
import br.com.shema.crm.catalog.domain.ProductDraft;
import br.com.shema.crm.catalog.domain.ProductFileKind;
import br.com.shema.crm.catalog.domain.SupplierDraft;
import br.com.shema.crm.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/catalog")
@Tag(name = "catalog", description = "Fornecedores e produtos por nicho")
class CatalogController {

    // ---- Nichos ----

    record NicheRequest(
            @Size(max = 80) String name, @Size(max = 300) String description) {}

    record UpdateNicheRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version,

            @NotNull @Valid NicheRequest niche) {}

    record CatalogVersionRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version) {}

    // ---- Fornecedores ----

    record SupplierContactRequest(
            @Size(max = 120) String name,
            @Size(max = 40) String phone,
            @Size(max = 254) String email) {}

    record SupplierRequest(
            @Size(max = 200) String legalName,
            @Size(max = 200) String tradeName,
            @Size(max = 30) String cnpj,
            @Size(max = 100) String city,
            @Size(max = 2) String state,
            @Size(max = 200) String website,
            @Size(max = 2000) String notes,
            @Size(max = 50) List<@NotNull UUID> nicheIds,
            @Size(max = 20) List<@NotNull @Valid SupplierContactRequest> contacts) {

        SupplierDraft toDraft() {
            return new SupplierDraft(
                    legalName,
                    tradeName,
                    cnpj,
                    city,
                    state,
                    website,
                    notes,
                    nicheIds == null ? List.of() : nicheIds,
                    contacts == null
                            ? List.of()
                            : contacts.stream()
                                    .map(c -> new SupplierDraft.Contact(c.name(), c.phone(), c.email()))
                                    .toList());
        }
    }

    record UpdateSupplierRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version,

            @NotNull @Valid SupplierRequest supplier) {}

    // ---- Produtos ----

    record ProductOfferRequest(
            UUID supplierId, BigDecimal unitPrice, LocalDate quotedOn, Integer leadTimeDays, BigDecimal minimumOrder) {}

    record ProductRequest(
            @Size(max = 200) String name,
            @Size(max = 2000) String description,
            UUID nicheId,
            @Size(max = 20) String unit,
            @Size(max = 100) String brand,
            @Size(max = 20) String catalogCode,
            @Size(max = 50) List<@NotNull @Valid ProductOfferRequest> offers) {

        ProductDraft toDraft() {
            return new ProductDraft(
                    name,
                    description,
                    nicheId,
                    unit,
                    brand,
                    catalogCode,
                    offers == null
                            ? List.of()
                            : offers.stream()
                                    .map(o -> new ProductDraft.Offer(
                                            o.supplierId(),
                                            o.unitPrice(),
                                            o.quotedOn(),
                                            o.leadTimeDays(),
                                            o.minimumOrder()))
                                    .toList());
        }
    }

    record UpdateProductRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version,

            @NotNull @Valid ProductRequest product) {}

    private final NicheService niches;
    private final SupplierService suppliers;
    private final ProductService products;

    CatalogController(NicheService niches, SupplierService suppliers, ProductService products) {
        this.niches = niches;
        this.suppliers = suppliers;
        this.products = products;
    }

    @GetMapping("/niches")
    @Operation(
            summary = "Nichos, por nome, com quantos fornecedores e produtos ativos tem cada um",
            operationId = "listNiches")
    List<NicheView> listNiches(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return niches.list(includeArchived);
    }

    @PostMapping("/niches")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Cria um nicho", operationId = "createNiche")
    NicheView createNiche(@Valid @RequestBody NicheRequest request) {
        return niches.create(request.name(), request.description());
    }

    @PutMapping("/niches/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Renomeia o nicho ou muda a descrição", operationId = "updateNiche")
    NicheView updateNiche(@PathVariable UUID id, @Valid @RequestBody UpdateNicheRequest request) {
        return niches.describe(
                id, request.version(), request.niche().name(), request.niche().description());
    }

    @PostMapping("/niches/{id}/archive")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Arquiva o nicho (os cadastros ligados continuam)", operationId = "archiveNiche")
    NicheView archiveNiche(@PathVariable UUID id, @Valid @RequestBody CatalogVersionRequest request) {
        return niches.archive(id, request.version());
    }

    @PostMapping("/niches/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Desarquiva o nicho", operationId = "restoreNiche")
    NicheView restoreNiche(@PathVariable UUID id, @Valid @RequestBody CatalogVersionRequest request) {
        return niches.restore(id, request.version());
    }

    @GetMapping("/suppliers")
    @Operation(
            summary = "Fornecedores, por razão social",
            description = "q acha pela razão social, nome fantasia, CNPJ ou cidade, sem diferenciar acento.",
            operationId = "searchSuppliers")
    PageResponse<SupplierSummary> searchSuppliers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID nicheId,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Sort order = Sort.by("legalName").and(Sort.by("id"));
        return PageResponse.of(
                suppliers.search(
                        q,
                        nicheId,
                        state,
                        includeArchived,
                        PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), order)),
                s -> s);
    }

    @GetMapping("/suppliers/options")
    @Operation(summary = "Fornecedores ativos, para escolher o de um preço", operationId = "supplierOptions")
    List<SupplierOption> supplierOptions() {
        return suppliers.options();
    }

    @GetMapping("/suppliers/{id}")
    @Operation(summary = "Um fornecedor, com contatos, nichos e os produtos que vende", operationId = "getSupplier")
    SupplierView getSupplier(@PathVariable UUID id) {
        return suppliers.get(id);
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Cadastra um fornecedor (só a razão social é obrigatória)", operationId = "createSupplier")
    SupplierView createSupplier(@Valid @RequestBody SupplierRequest request) {
        return suppliers.create(request.toDraft());
    }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Salva o fornecedor inteiro (dados, nichos e contatos)", operationId = "updateSupplier")
    SupplierView updateSupplier(@PathVariable UUID id, @Valid @RequestBody UpdateSupplierRequest request) {
        return suppliers.update(id, request.version(), request.supplier().toDraft());
    }

    @PostMapping("/suppliers/{id}/archive")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Arquiva o fornecedor (os preços continuam guardados)", operationId = "archiveSupplier")
    SupplierView archiveSupplier(@PathVariable UUID id, @Valid @RequestBody CatalogVersionRequest request) {
        return suppliers.archive(id, request.version());
    }

    @PostMapping("/suppliers/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Desarquiva o fornecedor", operationId = "restoreSupplier")
    SupplierView restoreSupplier(@PathVariable UUID id, @Valid @RequestBody CatalogVersionRequest request) {
        return suppliers.restore(id, request.version());
    }

    @GetMapping("/products")
    @Operation(
            summary = "Produtos, por nome, com o menor preço",
            description = "q acha pelo nome, marca ou código CATMAT/CATSER, sem diferenciar acento.",
            operationId = "searchProducts")
    PageResponse<ProductSummary> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID nicheId,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Sort order = Sort.by("name").and(Sort.by("id"));
        return PageResponse.of(
                products.search(
                        q,
                        nicheId,
                        supplierId,
                        includeArchived,
                        PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), order)),
                p -> p);
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "Um produto, com os preços dos fornecedores e os arquivos", operationId = "getProduct")
    ProductView getProduct(@PathVariable UUID id) {
        return products.get(id);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Cadastra um produto (nome e nicho são obrigatórios)", operationId = "createProduct")
    ProductView createProduct(@Valid @RequestBody ProductRequest request) {
        return products.create(request.toDraft());
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(
            summary = "Salva o produto inteiro (dados e preços)",
            description = "Se o preço de um fornecedor mudar e a data não, a data da cotação passa a ser hoje.",
            operationId = "updateProduct")
    ProductView updateProduct(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
        return products.update(id, request.version(), request.product().toDraft());
    }

    @PostMapping("/products/{id}/archive")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Arquiva o produto", operationId = "archiveProduct")
    ProductView archiveProduct(@PathVariable UUID id, @Valid @RequestBody CatalogVersionRequest request) {
        return products.archive(id, request.version());
    }

    @PostMapping("/products/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Desarquiva o produto", operationId = "restoreProduct")
    ProductView restoreProduct(@PathVariable UUID id, @Valid @RequestBody CatalogVersionRequest request) {
        return products.restore(id, request.version());
    }

    @PostMapping(path = "/products/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(
            summary = "Envia a foto ou a ficha técnica",
            description = "Até 10 MB. Foto: JPG, PNG ou WEBP; ficha: também PDF. O tipo é conferido pelo conteúdo.",
            operationId = "uploadProductFile")
    FileView uploadFile(
            @PathVariable UUID id, @RequestParam ProductFileKind kind, @RequestPart("file") MultipartFile file)
            throws IOException {
        return products.upload(id, kind, file.getOriginalFilename(), file.getBytes());
    }

    @GetMapping("/products/{id}/files/{fileId}")
    @Operation(summary = "Baixa a foto ou a ficha técnica", operationId = "downloadProductFile")
    @ApiResponse(
            responseCode = "200",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary")))
    ResponseEntity<byte[]> downloadFile(@PathVariable UUID id, @PathVariable UUID fileId) {
        ProductService.FileContent file = products.download(id, fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.content());
    }

    @DeleteMapping("/products/{id}/files/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Apaga a foto ou a ficha técnica", operationId = "deleteProductFile")
    void deleteFile(@PathVariable UUID id, @PathVariable UUID fileId) {
        products.deleteFile(id, fileId);
    }
}
