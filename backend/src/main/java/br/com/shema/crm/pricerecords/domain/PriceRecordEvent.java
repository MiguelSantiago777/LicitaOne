package br.com.shema.crm.pricerecords.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Linha do histórico da licitação sobre ata, caronas e documentos. Nunca é alterada nem apagada. */
@Entity
@Table(name = "price_record_events")
public class PriceRecordEvent {

    public enum Type {
        ATA_CREATED,
        ATA_UPDATED,
        ATA_DELETED,
        ITEMS_CHANGED,
        CARONA_REGISTERED,
        CARONA_REMOVED,
        DOCUMENT_ADDED,
        DOCUMENT_VERSION_ADDED,
        DOCUMENT_UPDATED,
        DOCUMENT_DELETED
    }

    @Id
    private UUID id;

    @Column(name = "tender_id", nullable = false)
    private UUID tenderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Type type;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "user_name", length = 120)
    private String userName;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected PriceRecordEvent() {}

    public PriceRecordEvent(UUID tenderId, Type type, String summary, UUID userId, String userName, Instant at) {
        this.id = UUID.randomUUID();
        this.tenderId = tenderId;
        this.type = type;
        this.summary = summary.length() > 500 ? summary.substring(0, 499) + "…" : summary;
        this.userId = userId;
        this.userName = userName;
        this.occurredAt = at;
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

    public String getSummary() {
        return summary;
    }

    public String getUserName() {
        return userName;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
