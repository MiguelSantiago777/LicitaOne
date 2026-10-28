package br.com.shema.crm.contracts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** Quantidade pedida de um item. O valor unitário é copiado do item no momento do pedido. */
@Entity
@Table(name = "contract_order_lines")
public class OrderLine {

    @Id
    private UUID id;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 4)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    protected OrderLine() {}

    OrderLine(UUID itemId, BigDecimal quantity, BigDecimal unitPrice) {
        this.id = UUID.randomUUID();
        this.itemId = itemId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.total = Quantities.total(quantity, unitPrice);
    }

    public UUID getId() {
        return id;
    }

    public UUID getItemId() {
        return itemId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getTotal() {
        return total;
    }
}
