package br.com.shema.crm.catalog.application;

import br.com.shema.crm.catalog.application.NicheService.NicheRef;
import br.com.shema.crm.catalog.domain.CatalogFileInspection;
import br.com.shema.crm.catalog.domain.Niche;
import br.com.shema.crm.catalog.domain.Product;
import br.com.shema.crm.catalog.domain.ProductDraft;
import br.com.shema.crm.catalog.domain.ProductFile;
import br.com.shema.crm.catalog.domain.ProductFileKind;
import br.com.shema.crm.catalog.domain.ProductOffer;
import br.com.shema.crm.catalog.domain.Supplier;
import br.com.shema.crm.catalog.infrastructure.ProductFileRepository;
import br.com.shema.crm.catalog.infrastructure.ProductRepository;
import br.com.shema.crm.catalog.infrastructure.SupplierRepository;
import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.security.CurrentUser;
import br.com.shema.crm.shared.storage.FileStorage;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Produtos: cadastro, preço de custo de cada fornecedor, menor preço, foto e ficha técnica. */
@Service
@Transactional
public class ProductService {

    public static final int MAX_FILES = 20;

    @Schema(name = "ProductSummaryResponse")
    public record ProductSummary(
            UUID id,
            String name,
            NicheRef niche,
            String unit,
            String brand,
            String catalogCode,
            int supplierCount,
            BigDecimal lowestPrice,
            String lowestSupplierName,
            LocalDate lowestQuotedOn,
            boolean lowestStale,
            Instant archivedAt) {}

    @Schema(name = "ProductResponse")
    public record ProductView(
            UUID id,
            String name,
            String description,
            NicheRef niche,
            String unit,
            String brand,
            String catalogCode,
            List<OfferView> offers,
            UUID lowestOfferSupplierId,
            List<FileView> files,
            Instant archivedAt,
            long version,
            boolean canEdit) {}

    @Schema(name = "ProductOfferResponse")
    public record OfferView(
            UUID id,
            UUID supplierId,
            String supplierName,
            boolean supplierArchived,
            BigDecimal unitPrice,
            LocalDate quotedOn,
            boolean stale,
            Integer leadTimeDays,
            BigDecimal minimumOrder) {}

    @Schema(name = "ProductFileResponse")
    public record FileView(
            UUID id,
            ProductFileKind kind,
            String fileName,
            String contentType,
            long sizeBytes,
            String uploadedBy,
            Instant uploadedAt) {}

    /** O conteúdo de um arquivo para baixar ou mostrar. */
    public record FileContent(String fileName, String contentType, byte[] content) {}

    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final ProductFileRepository files;
    private final NicheService niches;
    private final FileStorage storage;
    private final Clock clock;

    public ProductService(
            ProductRepository products,
            SupplierRepository suppliers,
            ProductFileRepository files,
            NicheService niches,
            FileStorage storage,
            Clock clock) {
        this.products = products;
        this.suppliers = suppliers;
        this.files = files;
        this.niches = niches;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<ProductSummary> search(
            String q, UUID nicheId, UUID supplierId, boolean includeArchived, Pageable pageable) {
        Page<Product> page =
                products.findAll(CatalogSpecifications.products(q, nicheId, supplierId, includeArchived), pageable);
        if (page.isEmpty()) {
            return page.map(p -> null);
        }
        Map<UUID, Niche> allNiches = niches.byId();
        Set<UUID> supplierIds = new HashSet<>();
        page.forEach(p -> supplierIds.addAll(p.supplierIds()));
        Map<UUID, Supplier> bySupplier = suppliersById(supplierIds);
        LocalDate today = LocalDate.now(clock);
        return page.map(p -> {
            Optional<ProductOffer> best = p.bestOffer(active(bySupplier));
            Supplier bestSupplier =
                    best.map(o -> bySupplier.get(o.getSupplierId())).orElse(null);
            return new ProductSummary(
                    p.getId(),
                    p.getName(),
                    ref(allNiches.get(p.getNicheId())),
                    p.getUnit(),
                    p.getBrand(),
                    p.getCatalogCode(),
                    p.getOffers().size(),
                    best.map(ProductOffer::getUnitPrice).orElse(null),
                    bestSupplier == null ? null : bestSupplier.displayName(),
                    best.map(ProductOffer::getQuotedOn).orElse(null),
                    best.map(o -> o.isStale(today)).orElse(false),
                    p.getArchivedAt());
        });
    }

    @Transactional(readOnly = true)
    public ProductView get(UUID id) {
        return view(load(id));
    }

    public ProductView create(ProductDraft draft) {
        CatalogAccess.requireWriter();
        requireReferences(draft, null);
        return view(products.saveAndFlush(new Product(draft, LocalDate.now(clock))));
    }

    public ProductView update(UUID id, long version, ProductDraft draft) {
        CatalogAccess.requireWriter();
        Product product = CatalogAccess.requireVersion(load(id), version, "este produto");
        requireReferences(draft, product);
        product.update(draft, LocalDate.now(clock));
        products.flush();
        return view(product);
    }

    public ProductView archive(UUID id, long version) {
        CatalogAccess.requireWriter();
        Product product = CatalogAccess.requireVersion(load(id), version, "este produto");
        product.archive(Instant.now(clock));
        products.flush();
        return view(product);
    }

    public ProductView restore(UUID id, long version) {
        CatalogAccess.requireWriter();
        Product product = CatalogAccess.requireVersion(load(id), version, "este produto");
        product.restore();
        products.flush();
        return view(product);
    }

    /** Guarda a foto ou a ficha técnica. Não muda a versão do produto: quem está editando não toma conflito. */
    public FileView upload(UUID productId, ProductFileKind kind, String fileName, byte[] content) {
        CurrentUser me = CatalogAccess.requireWriter();
        load(productId);
        if (kind == null) {
            throw new DomainRuleViolation("REQUIRED", "Diga se o arquivo é foto ou ficha técnica.");
        }
        if (files.countByProductId(productId) >= MAX_FILES) {
            throw new DomainRuleViolation(
                    "TOO_MANY_FILES", "Um produto tem no máximo " + MAX_FILES + " arquivos. Apague algum antes.");
        }
        CatalogFileInspection.Accepted accepted = CatalogFileInspection.inspect(kind, fileName, content);
        ProductFile file = new ProductFile(productId, kind, accepted, content.length, me.name(), Instant.now(clock));
        storage.put(file.getStorageKey(), content);
        // Se o banco não gravar, o arquivo não fica órfão no armazenamento
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.delete(file.getStorageKey());
                }
            }
        });
        return fileView(files.saveAndFlush(file));
    }

    @Transactional(readOnly = true)
    public FileContent download(UUID productId, UUID fileId) {
        ProductFile file = loadFile(productId, fileId);
        return new FileContent(file.getFileName(), file.getContentType(), storage.get(file.getStorageKey()));
    }

    /** Apaga o registro e o arquivo guardado (foto e ficha não têm histórico). */
    public void deleteFile(UUID productId, UUID fileId) {
        CatalogAccess.requireWriter();
        ProductFile file = loadFile(productId, fileId);
        files.delete(file);
        files.flush();
        String key = file.getStorageKey();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.delete(key);
            }
        });
    }

    /**
     * Nicho e fornecedores precisam existir. Nicho trocado agora e fornecedor que entra agora não podem estar
     * arquivados; os que já estavam ficam.
     */
    private void requireReferences(ProductDraft draft, Product current) {
        if (draft.nicheId() != null) {
            niches.requireAssignable(
                    List.of(draft.nicheId()), current == null ? List.of() : List.of(current.getNicheId()));
        }
        Set<UUID> wanted = draft.offers() == null
                ? Set.of()
                : draft.offers().stream()
                        .map(ProductDraft.Offer::supplierId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        if (wanted.isEmpty()) {
            return;
        }
        Set<UUID> already = current == null ? Set.of() : current.supplierIds();
        Map<UUID, Supplier> found = suppliersById(wanted);
        for (UUID id : wanted) {
            Supplier supplier = found.get(id);
            if (supplier == null) {
                throw new ApiException(ErrorCode.NOT_FOUND, "Fornecedor não encontrado.");
            }
            if (supplier.isArchived() && !already.contains(id)) {
                throw new ApiException(
                        ErrorCode.BUSINESS_RULE,
                        "O fornecedor " + supplier.displayName() + " está arquivado. Desarquive-o antes.");
            }
        }
    }

    private Product load(UUID id) {
        return products.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Produto não encontrado."));
    }

    private ProductFile loadFile(UUID productId, UUID fileId) {
        return files.findById(fileId)
                .filter(f -> f.getProductId().equals(productId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Arquivo não encontrado."));
    }

    private Map<UUID, Supplier> suppliersById(Collection<UUID> ids) {
        return ids.isEmpty()
                ? Map.of()
                : suppliers.findAllById(ids).stream().collect(Collectors.toMap(Supplier::getId, Function.identity()));
    }

    /** Fornecedor arquivado não entra no menor preço. */
    private static java.util.function.Predicate<UUID> active(Map<UUID, Supplier> bySupplier) {
        return id -> {
            Supplier s = bySupplier.get(id);
            return s != null && !s.isArchived();
        };
    }

    private ProductView view(Product p) {
        Map<UUID, Supplier> bySupplier = suppliersById(p.supplierIds());
        LocalDate today = LocalDate.now(clock);
        Niche niche = niches.byId().get(p.getNicheId());
        List<OfferView> offers = p.getOffers().stream()
                .map(o -> {
                    Supplier s = bySupplier.get(o.getSupplierId());
                    return new OfferView(
                            o.getId(),
                            o.getSupplierId(),
                            s == null ? null : s.displayName(),
                            s != null && s.isArchived(),
                            o.getUnitPrice(),
                            o.getQuotedOn(),
                            o.isStale(today),
                            o.getLeadTimeDays(),
                            o.getMinimumOrder());
                })
                .toList();
        return new ProductView(
                p.getId(),
                p.getName(),
                p.getDescription(),
                ref(niche),
                p.getUnit(),
                p.getBrand(),
                p.getCatalogCode(),
                offers,
                p.bestOffer(active(bySupplier)).map(ProductOffer::getSupplierId).orElse(null),
                files.findByProductIdOrderByUploadedAtDesc(p.getId()).stream()
                        .map(ProductService::fileView)
                        .toList(),
                p.getArchivedAt(),
                p.getVersion() == null ? 0 : p.getVersion(),
                CatalogAccess.canWrite());
    }

    private static NicheRef ref(Niche niche) {
        return niche == null ? null : NicheRef.of(niche);
    }

    private static FileView fileView(ProductFile f) {
        return new FileView(
                f.getId(),
                f.getKind(),
                f.getFileName(),
                f.getContentType(),
                f.getSizeBytes(),
                f.getUploadedBy(),
                f.getUploadedAt());
    }
}
