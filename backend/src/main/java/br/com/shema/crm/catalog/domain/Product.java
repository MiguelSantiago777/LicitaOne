package br.com.shema.crm.catalog.domain;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.hibernate.annotations.BatchSize;

/**
 * Produto de um nicho, com o preço de custo de cada fornecedor (ofertas). O menor preço é o que a lista mostra.
 * Arquivado, sai da lista, mas continua consultável.
 */
@Entity
@Table(name = "products")
public class Product extends AuditableEntity {

    public static final int MAX_OFFERS = 50;

    /** Cotação mais velha que isso aparece como antiga (decisão de 23/11/2026). */
    public static final int STALE_AFTER_DAYS = 90;

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "niche_id", nullable = false)
    private UUID nicheId;

    @Column(length = 20)
    private String unit;

    @Column(length = 100)
    private String brand;

    @Column(name = "catalog_code", length = 20)
    private String catalogCode;

    @Column(name = "search_text", nullable = false, length = 400)
    private String searchText;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "product_id", nullable = false)
    @OrderBy("position")
    @BatchSize(size = 100)
    private List<ProductOffer> offers = new ArrayList<>();

    protected Product() {}

    /** Produto novo: nome e nicho são obrigatórios; o resto a pessoa completa na tela. */
    public Product(ProductDraft draft, LocalDate today) {
        this.id = UUID.randomUUID();
        update(draft, today);
    }

    /**
     * Troca tudo pelo que veio. Nicho e fornecedores já foram conferidos pelo serviço. As ofertas são casadas pelo
     * fornecedor: a que já existia mantém o id e a data, a não ser que o preço mude.
     */
    public void update(ProductDraft d, LocalDate today) {
        this.name = CatalogText.required(d.name(), 200, "no nome do produto", "Informe o nome do produto.");
        this.description = CatalogText.optional(d.description(), 2000, "na descrição");
        if (d.nicheId() == null) {
            throw new DomainRuleViolation("REQUIRED", "Escolha o nicho do produto.");
        }
        this.nicheId = d.nicheId();
        this.unit = CatalogText.optional(d.unit(), 20, "na unidade");
        this.brand = CatalogText.optional(d.brand(), 100, "na marca");
        this.catalogCode = catalogCode(d.catalogCode());
        applyOffers(d.offers() == null ? List.of() : d.offers(), today);
        this.searchText = CatalogText.searchText(400, name, brand, catalogCode);
    }

    private void applyOffers(List<ProductDraft.Offer> drafts, LocalDate today) {
        if (drafts.size() > MAX_OFFERS) {
            throw new DomainRuleViolation(
                    "TOO_MANY_OFFERS", "Um produto tem no máximo " + MAX_OFFERS + " fornecedores.");
        }
        Set<UUID> seen = new HashSet<>();
        List<ProductOffer> next = new ArrayList<>();
        for (ProductDraft.Offer draft : drafts) {
            if (draft.supplierId() == null) {
                throw new DomainRuleViolation("REQUIRED", "Escolha o fornecedor de cada preço.");
            }
            if (!seen.add(draft.supplierId())) {
                throw new DomainRuleViolation(
                        "SUPPLIER_REPEATED", "O mesmo fornecedor aparece duas vezes. Deixe um preço por fornecedor.");
            }
            ProductOffer offer = offers.stream()
                    .filter(o -> o.getSupplierId().equals(draft.supplierId()))
                    .findFirst()
                    .orElseGet(() -> new ProductOffer(draft.supplierId()));
            offer.apply(next.size() + 1, draft, today);
            next.add(offer);
        }
        offers.retainAll(next);
        next.stream().filter(o -> !offers.contains(o)).forEach(offers::add);
        offers.sort(Comparator.comparingInt(ProductOffer::getPosition));
    }

    /** CATMAT e CATSER são números; aceita com pontos ou traço e guarda só os dígitos. */
    private static String catalogCode(String value) {
        String text = CatalogText.optional(value, 20, "no código CATMAT/CATSER");
        if (text == null) {
            return null;
        }
        String digits = text.replaceAll("[\\s.\\-]", "");
        if (!digits.matches("\\d{1,12}")) {
            throw new DomainRuleViolation("CATALOG_CODE_INVALID", "O código CATMAT/CATSER tem só números.");
        }
        return digits;
    }

    /**
     * A oferta mais barata entre os fornecedores aceitos (o serviço tira os arquivados). No empate, a cotação mais
     * recente. Vazio se nenhum tem preço.
     */
    public Optional<ProductOffer> bestOffer(Predicate<UUID> supplierAccepted) {
        return offers.stream()
                .filter(o -> o.getUnitPrice() != null)
                .filter(o -> supplierAccepted.test(o.getSupplierId()))
                .min(Comparator.comparing(ProductOffer::getUnitPrice)
                        .thenComparing(ProductOffer::getQuotedOn, Comparator.nullsLast(Comparator.reverseOrder())));
    }

    public void archive(Instant at) {
        if (archivedAt != null) {
            throw new DomainRuleViolation("ALREADY_ARCHIVED", "O produto já está arquivado.");
        }
        this.archivedAt = at;
    }

    public void restore() {
        if (archivedAt == null) {
            throw new DomainRuleViolation("NOT_ARCHIVED", "O produto não está arquivado.");
        }
        this.archivedAt = null;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public Set<UUID> supplierIds() {
        Set<UUID> ids = new HashSet<>();
        offers.stream()
                .map(ProductOffer::getSupplierId)
                .filter(Objects::nonNull)
                .forEach(ids::add);
        return ids;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public UUID getNicheId() {
        return nicheId;
    }

    public String getUnit() {
        return unit;
    }

    public String getBrand() {
        return brand;
    }

    public String getCatalogCode() {
        return catalogCode;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public List<ProductOffer> getOffers() {
        return List.copyOf(offers);
    }
}
