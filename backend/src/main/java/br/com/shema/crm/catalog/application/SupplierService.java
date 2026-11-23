package br.com.shema.crm.catalog.application;

import br.com.shema.crm.catalog.application.NicheService.NicheRef;
import br.com.shema.crm.catalog.domain.Niche;
import br.com.shema.crm.catalog.domain.Product;
import br.com.shema.crm.catalog.domain.ProductOffer;
import br.com.shema.crm.catalog.domain.Supplier;
import br.com.shema.crm.catalog.domain.SupplierContact;
import br.com.shema.crm.catalog.domain.SupplierDraft;
import br.com.shema.crm.catalog.infrastructure.ProductRepository;
import br.com.shema.crm.catalog.infrastructure.SupplierRepository;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fornecedores: cadastro, contatos, nichos que atendem e os produtos que vendem. */
@Service
@Transactional
public class SupplierService {

    @Schema(name = "SupplierSummaryResponse")
    public record SupplierSummary(
            UUID id,
            String legalName,
            String tradeName,
            String cnpj,
            String city,
            String state,
            List<NicheRef> niches,
            long productCount,
            Instant archivedAt) {}

    @Schema(name = "SupplierResponse")
    public record SupplierView(
            UUID id,
            String legalName,
            String tradeName,
            String cnpj,
            String city,
            String state,
            String website,
            String notes,
            List<NicheRef> niches,
            List<ContactView> contacts,
            List<SupplierProductView> products,
            Instant archivedAt,
            long version,
            boolean canEdit) {}

    @Schema(name = "SupplierContactResponse")
    public record ContactView(UUID id, String name, String phone, String email) {}

    /** Um produto que o fornecedor vende, com o preço dele e se é o menor entre os fornecedores. */
    @Schema(name = "SupplierProductResponse")
    public record SupplierProductView(
            UUID productId,
            String name,
            String nicheName,
            String unit,
            String brand,
            BigDecimal unitPrice,
            LocalDate quotedOn,
            boolean stale,
            boolean lowestPrice,
            boolean archived) {}

    /** Para escolher o fornecedor de um preço. */
    @Schema(name = "SupplierOptionResponse")
    public record SupplierOption(UUID id, String name, String cnpj, Set<UUID> nicheIds) {}

    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final NicheService niches;
    private final Clock clock;

    public SupplierService(SupplierRepository suppliers, ProductRepository products, NicheService niches, Clock clock) {
        this.suppliers = suppliers;
        this.products = products;
        this.niches = niches;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<SupplierSummary> search(
            String q, UUID nicheId, String state, boolean includeArchived, Pageable pageable) {
        Page<Supplier> page =
                suppliers.findAll(CatalogSpecifications.suppliers(q, nicheId, state, includeArchived), pageable);
        List<UUID> ids = page.getContent().stream().map(Supplier::getId).toList();
        Map<UUID, Long> counts = ids.isEmpty()
                ? Map.of()
                : suppliers.countActiveProducts(ids).stream()
                        .collect(Collectors.toMap(
                                SupplierRepository.SupplierCount::getSupplierId,
                                SupplierRepository.SupplierCount::getTotal));
        Map<UUID, Niche> allNiches = page.isEmpty() ? Map.of() : niches.byId();
        return page.map(s -> new SupplierSummary(
                s.getId(),
                s.getLegalName(),
                s.getTradeName(),
                s.getCnpj(),
                s.getCity(),
                s.getState(),
                refs(s.getNicheIds(), allNiches),
                counts.getOrDefault(s.getId(), 0L),
                s.getArchivedAt()));
    }

    @Transactional(readOnly = true)
    public List<SupplierOption> options() {
        return suppliers.findByArchivedAtIsNullOrderByLegalName().stream()
                .map(s -> new SupplierOption(s.getId(), s.displayName(), s.getCnpj(), s.getNicheIds()))
                .toList();
    }

    @Transactional(readOnly = true)
    public SupplierView get(UUID id) {
        return view(load(id));
    }

    public SupplierView create(SupplierDraft draft) {
        CatalogAccess.requireWriter();
        niches.requireAssignable(nicheIds(draft), Set.of());
        return view(suppliers.saveAndFlush(new Supplier(draft)));
    }

    public SupplierView update(UUID id, long version, SupplierDraft draft) {
        CatalogAccess.requireWriter();
        Supplier supplier = CatalogAccess.requireVersion(load(id), version, "este fornecedor");
        niches.requireAssignable(nicheIds(draft), supplier.getNicheIds());
        supplier.update(draft);
        suppliers.flush();
        return view(supplier);
    }

    public SupplierView archive(UUID id, long version) {
        CatalogAccess.requireWriter();
        Supplier supplier = CatalogAccess.requireVersion(load(id), version, "este fornecedor");
        supplier.archive(Instant.now(clock));
        suppliers.flush();
        return view(supplier);
    }

    public SupplierView restore(UUID id, long version) {
        CatalogAccess.requireWriter();
        Supplier supplier = CatalogAccess.requireVersion(load(id), version, "este fornecedor");
        supplier.restore();
        suppliers.flush();
        return view(supplier);
    }

    private static List<UUID> nicheIds(SupplierDraft draft) {
        return draft.nicheIds() == null
                ? List.of()
                : draft.nicheIds().stream().filter(Objects::nonNull).distinct().toList();
    }

    private Supplier load(UUID id) {
        return suppliers
                .findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Fornecedor não encontrado."));
    }

    private SupplierView view(Supplier s) {
        Map<UUID, Niche> allNiches = niches.byId();
        LocalDate today = LocalDate.now(clock);
        List<Product> offered = products.findOfferedBy(s.getId());
        // Para dizer se o preço deste fornecedor é o menor, os outros fornecedores arquivados não contam
        Set<UUID> others =
                offered.stream().flatMap(p -> p.supplierIds().stream()).collect(Collectors.toSet());
        Set<UUID> archived = suppliers.findAllById(others).stream()
                .filter(Supplier::isArchived)
                .map(Supplier::getId)
                .collect(Collectors.toSet());
        List<SupplierProductView> productViews = offered.stream()
                .map(p -> {
                    ProductOffer mine = p.getOffers().stream()
                            .filter(o -> o.getSupplierId().equals(s.getId()))
                            .findFirst()
                            .orElseThrow();
                    boolean lowest = mine.getUnitPrice() != null
                            && p.bestOffer(id -> id.equals(s.getId()) || !archived.contains(id))
                                    .map(best -> best.getUnitPrice().compareTo(mine.getUnitPrice()) == 0)
                                    .orElse(false);
                    Niche niche = allNiches.get(p.getNicheId());
                    return new SupplierProductView(
                            p.getId(),
                            p.getName(),
                            niche == null ? null : niche.getName(),
                            p.getUnit(),
                            p.getBrand(),
                            mine.getUnitPrice(),
                            mine.getQuotedOn(),
                            mine.isStale(today),
                            lowest,
                            p.isArchived());
                })
                .toList();
        return new SupplierView(
                s.getId(),
                s.getLegalName(),
                s.getTradeName(),
                s.getCnpj(),
                s.getCity(),
                s.getState(),
                s.getWebsite(),
                s.getNotes(),
                refs(s.getNicheIds(), allNiches),
                s.getContacts().stream().map(SupplierService::contact).toList(),
                productViews,
                s.getArchivedAt(),
                s.getVersion() == null ? 0 : s.getVersion(),
                CatalogAccess.canWrite());
    }

    private static ContactView contact(SupplierContact c) {
        return new ContactView(c.getId(), c.getName(), c.getPhone(), c.getEmail());
    }

    static List<NicheRef> refs(Set<UUID> ids, Map<UUID, Niche> all) {
        return ids.stream()
                .map(all::get)
                .filter(Objects::nonNull)
                .map(NicheRef::of)
                .sorted(Comparator.comparing(NicheRef::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
