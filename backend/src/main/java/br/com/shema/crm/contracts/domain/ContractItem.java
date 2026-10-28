package br.com.shema.crm.contracts.domain;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** Item do contrato: o que foi contratado, em que quantidade e a que preço. Parte do agregado {@link Contract}. */
@Entity
@Table(name = "contract_items")
public class ContractItem {

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

    protected ContractItem() {}

    ContractItem(int position, ItemDraft draft) {
        this.id = UUID.randomUUID();
        this.position = position;
        apply(draft);
    }

    void apply(ItemDraft d) {
        if (d.description() == null || d.description().isBlank()) {
            throw new DomainRuleViolation("REQUIRED", "Descreva o item.");
        }
        this.lot = blankToNull(d.lot());
        this.description = d.description().trim();
        this.unit = blankToNull(d.unit());
        this.quantity = Quantities.quantity(d.quantity(), "contratada");
        this.unitPrice = Quantities.unitPrice(d.unitPrice());
    }

    void moveTo(int position) {
        this.position = position;
    }

    public BigDecimal total() {
        return Quantities.total(quantity, unitPrice);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
