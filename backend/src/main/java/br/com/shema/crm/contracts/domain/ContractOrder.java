package br.com.shema.crm.contracts.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pedido do órgão contra um contrato (empenho, autorização de fornecimento, ordem de compra). Parte do agregado
 * {@link Contract}: só é criado por {@link Contract#placeOrder}, que valida o saldo.
 */
@Entity
@Table(name = "contract_orders")
public class ContractOrder {

    @Id
    private UUID id;

    @Column(name = "ordered_at", nullable = false)
    private LocalDate orderedAt;

    /** Número do empenho, da AF ou da ordem de compra, se houver. */
    @Column(length = 60)
    private String reference;

    @Column(length = 2000)
    private String notes;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal value;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by_name", length = 120)
    private String createdByName;

    protected ContractOrder() {}

    ContractOrder(
            LocalDate orderedAt,
            String reference,
            String notes,
            BigDecimal value,
            List<OrderLine> lines,
            Instant createdAt,
            String createdByName) {
        this.id = UUID.randomUUID();
        this.orderedAt = orderedAt;
        this.reference = reference;
        this.notes = notes;
        this.value = value;
        this.lines.addAll(lines);
        this.createdAt = createdAt;
        this.createdByName = createdByName;
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getOrderedAt() {
        return orderedAt;
    }

    public String getReference() {
        return reference;
    }

    public String getNotes() {
        return notes;
    }

    public BigDecimal getValue() {
        return value;
    }

    public List<OrderLine> getLines() {
        return List.copyOf(lines);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedByName() {
        return createdByName;
    }
}
