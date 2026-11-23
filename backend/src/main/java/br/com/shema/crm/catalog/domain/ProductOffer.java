package br.com.shema.crm.catalog.domain;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.domain.Quantities;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Preço de custo de um fornecedor para um produto. Parte do agregado {@link Product}; um por fornecedor. Quando o
 * preço muda e a pessoa não mexeu na data, a data da cotação passa a ser hoje.
 */
@Entity
@Table(name = "product_offers")
public class ProductOffer {

    public static final int MAX_LEAD_TIME_DAYS = 3650;

    @Id
    private UUID id;

    @Column(name = "supplier_id", nullable = false, updatable = false)
    private UUID supplierId;

    @Column(nullable = false)
    private int position;

    @Column(name = "unit_price", precision = 15, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "quoted_on")
    private LocalDate quotedOn;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "minimum_order", precision = 15, scale = 3)
    private BigDecimal minimumOrder;

    protected ProductOffer() {}

    ProductOffer(UUID supplierId) {
        this.id = UUID.randomUUID();
        this.supplierId = Objects.requireNonNull(supplierId, "supplierId");
    }

    void apply(int position, ProductDraft.Offer draft, LocalDate today) {
        BigDecimal price = draft.unitPrice() == null ? null : Quantities.unitPrice(draft.unitPrice());
        LocalDate date = draft.quotedOn();
        if (date != null && date.isAfter(today)) {
            throw new DomainRuleViolation("QUOTE_DATE_FUTURE", "A data da cotação não pode ser depois de hoje.");
        }
        boolean priceChanged = price != null && (unitPrice == null || price.compareTo(unitPrice) != 0);
        boolean dateUntouched = Objects.equals(date, quotedOn);
        if (priceChanged && (date == null || dateUntouched)) {
            date = today;
        }
        Integer lead = draft.leadTimeDays();
        if (lead != null && (lead < 0 || lead > MAX_LEAD_TIME_DAYS)) {
            throw new DomainRuleViolation(
                    "LEAD_TIME_INVALID", "O prazo de entrega vai de 0 a " + MAX_LEAD_TIME_DAYS + " dias.");
        }
        this.position = position;
        this.unitPrice = price;
        this.quotedOn = date;
        this.leadTimeDays = lead;
        this.minimumOrder =
                draft.minimumOrder() == null ? null : Quantities.quantity(draft.minimumOrder(), "do pedido mínimo");
    }

    /** Cotação com mais de {@link Product#STALE_AFTER_DAYS} dias: só um aviso na tela. */
    public boolean isStale(LocalDate today) {
        return quotedOn != null && quotedOn.isBefore(today.minusDays(Product.STALE_AFTER_DAYS));
    }

    public UUID getId() {
        return id;
    }

    public UUID getSupplierId() {
        return supplierId;
    }

    public int getPosition() {
        return position;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public LocalDate getQuotedOn() {
        return quotedOn;
    }

    public Integer getLeadTimeDays() {
        return leadTimeDays;
    }

    public BigDecimal getMinimumOrder() {
        return minimumOrder;
    }
}
