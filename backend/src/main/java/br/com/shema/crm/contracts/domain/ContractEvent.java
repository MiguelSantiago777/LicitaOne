package br.com.shema.crm.contracts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Linha do histórico de um contrato: quem fez o quê e quando. Nunca é alterada nem apagada. */
@Entity
@Table(name = "contract_events")
public class ContractEvent {

    public enum Type {
        SIGNED,
        UPDATED,
        ITEMS_CHANGED,
        ORDER_PLACED,
        ORDER_REMOVED,
        CLOSED
    }

    @Id
    private UUID id;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(name = "tender_id", nullable = false)
    private UUID tenderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(name = "event_date")
    private LocalDate eventDate;

    /** Resumo legível ("Pedido de R$ 12.000,00 · AF 123"). */
    @Column(length = 500)
    private String summary;

    /** JSON com a lista de campos alterados: [{"field":..,"from":..,"to":..}] */
    @Column(columnDefinition = "text")
    private String changes;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "user_name", length = 120)
    private String userName;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ContractEvent() {}

    public ContractEvent(
            UUID contractId,
            UUID tenderId,
            Type type,
            LocalDate eventDate,
            String summary,
            String changes,
            UUID userId,
            String userName,
            Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.contractId = contractId;
        this.tenderId = tenderId;
        this.type = type;
        this.eventDate = eventDate;
        this.summary = summary;
        this.changes = changes;
        this.userId = userId;
        this.userName = userName;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getContractId() {
        return contractId;
    }

    public UUID getTenderId() {
        return tenderId;
    }

    public Type getType() {
        return type;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public String getSummary() {
        return summary;
    }

    public String getChanges() {
        return changes;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
