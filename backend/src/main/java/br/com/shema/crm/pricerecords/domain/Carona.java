package br.com.shema.crm.pricerecords.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Adesão de outro órgão à ata ("pegar carona"). Parte de {@link PriceRecord}, que confere os limites. Não é editada:
 * carona errada é apagada e registrada de novo, e o histórico guarda as duas coisas.
 */
@Entity
@Table(name = "caronas")
public class Carona {

    /** Quantidade de um item da ata que o órgão aderiu. */
    @Embeddable
    public record Line(
            @Column(name = "item_id", nullable = false) UUID itemId,

            @Column(nullable = false, precision = 15, scale = 3)
            BigDecimal quantity) {}

    @Id
    private UUID id;

    @Column(name = "agency_id", nullable = false, updatable = false)
    private UUID agencyId;

    @Column(name = "adhered_at", nullable = false, updatable = false)
    private LocalDate adheredAt;

    @Column(nullable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal value;

    @Column(name = "deal_id")
    private UUID dealId;

    @Column(length = 2000, updatable = false)
    private String notes;

    @Column(name = "limits_overridden", nullable = false, updatable = false)
    private boolean limitsOverridden;

    @Column(name = "override_note", length = 1000, updatable = false)
    private String overrideNote;

    @Column(name = "created_by_name", length = 120, updatable = false)
    private String createdByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ElementCollection
    @CollectionTable(name = "carona_lines", joinColumns = @JoinColumn(name = "carona_id"))
    private List<Line> lines = new ArrayList<>();

    protected Carona() {}

    Carona(
            UUID agencyId,
            LocalDate adheredAt,
            BigDecimal value,
            List<Line> lines,
            UUID dealId,
            String notes,
            String overrideNote,
            String createdByName,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.agencyId = agencyId;
        this.adheredAt = adheredAt;
        this.value = value;
        this.lines = new ArrayList<>(lines);
        this.dealId = dealId;
        this.notes = notes;
        this.limitsOverridden = overrideNote != null;
        this.overrideNote = overrideNote;
        this.createdByName = createdByName;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAgencyId() {
        return agencyId;
    }

    public LocalDate getAdheredAt() {
        return adheredAt;
    }

    public BigDecimal getValue() {
        return value;
    }

    public UUID getDealId() {
        return dealId;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isLimitsOverridden() {
        return limitsOverridden;
    }

    public String getOverrideNote() {
        return overrideNote;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Line> getLines() {
        return List.copyOf(lines);
    }
}
