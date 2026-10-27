package br.com.shema.crm.tenders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Linha do histórico de uma licitação: quem fez o quê e quando. Nunca é alterada nem apagada. */
@Entity
@Table(name = "tender_events")
public class TenderEvent {

    public enum Type {
        CREATED,
        UPDATED,
        STATUS_CHANGED
    }

    @Id
    private UUID id;

    @Column(name = "tender_id", nullable = false)
    private UUID tenderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private TenderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 30)
    private TenderStatus toStatus;

    @Column(name = "event_date")
    private LocalDate eventDate;

    /** JSON com a lista de campos alterados: [{"field":..,"from":..,"to":..}] */
    @Column(columnDefinition = "text")
    private String changes;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "user_name", length = 120)
    private String userName;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected TenderEvent() {}

    public TenderEvent(
            UUID tenderId,
            Type type,
            TenderStatus fromStatus,
            TenderStatus toStatus,
            LocalDate eventDate,
            String changes,
            UUID userId,
            String userName,
            Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.tenderId = tenderId;
        this.type = type;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.eventDate = eventDate;
        this.changes = changes;
        this.userId = userId;
        this.userName = userName;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenderId() {
        return tenderId;
    }

    public Type getType() {
        return type;
    }

    public TenderStatus getFromStatus() {
        return fromStatus;
    }

    public TenderStatus getToStatus() {
        return toStatus;
    }

    public LocalDate getEventDate() {
        return eventDate;
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
