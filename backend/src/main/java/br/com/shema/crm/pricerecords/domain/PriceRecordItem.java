package br.com.shema.crm.pricerecords.domain;

import br.com.shema.crm.pricerecords.domain.PriceRecordDrafts.ItemDraft;
import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.domain.Quantities;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** Item registrado na ata: o quantitativo que serve de base para os limites de carona. Parte de {@link PriceRecord}. */
@Entity
@Table(name = "price_record_items")
public class PriceRecordItem {

    @Id
    private UUID id;

    @Column(nullable = false)
    private int position;

    @Column(length = 40)
    private String lot;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(length = 20)
    private String unit;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 4)
    private BigDecimal unitPrice;

    protected PriceRecordItem() {}

    PriceRecordItem(int position, ItemDraft draft) {
        this.id = UUID.randomUUID();
        this.position = position;
        apply(draft);
    }

    void apply(ItemDraft d) {
        if (d.description() == null || d.description().isBlank()) {
            throw new DomainRuleViolation("REQUIRED", "Descreva o item.");
        }
        if (d.description().trim().length() > 500) {
            throw new DomainRuleViolation("TOO_LONG", "Use no máximo 500 caracteres na descrição do item.");
        }
        this.lot = blankToNull(d.lot(), 40);
        this.description = d.description().trim();
        this.unit = blankToNull(d.unit(), 20);
        this.quantity = Quantities.quantity(d.quantity(), "registrada");
        this.unitPrice = Quantities.unitPrice(d.unitPrice());
    }

    void moveTo(int position) {
        this.position = position;
    }

    public BigDecimal total() {
        return Quantities.total(quantity, unitPrice);
    }

    private static String blankToNull(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    public UUID getId() {
        return id;
    }

    public int getPosition() {
        return position;
    }

    public String getLot() {
        return lot;
    }

    public String getDescription() {
        return description;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}
