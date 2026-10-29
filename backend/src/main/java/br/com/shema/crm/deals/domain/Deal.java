package br.com.shema.crm.deals.domain;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.domain.Money;
import br.com.shema.crm.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.OptimisticLock;

/**
 * Negociação do funil. O status acompanha o tipo da etapa e é gravado só para filtrar sem juntar tabelas. A versão
 * protege o conteúdo; mudar de etapa ou de posição não mexe nela (como nos cards dos quadros).
 */
@Entity
@Table(name = "deals")
public class Deal extends AuditableEntity {

    public static final int MIN_PRIORITY = 1;
    public static final int MAX_PRIORITY = 3;

    /** Por quanto tempo a negociação aparece como "Nova" no card. */
    public static final Duration NEW_FOR = Duration.ofDays(7);

    @Id
    private UUID id;

    @Column(name = "pipeline_id", nullable = false)
    private UUID pipelineId;

    @OptimisticLock(excluded = true)
    @Column(name = "stage_id", nullable = false)
    private UUID stageId;

    @OptimisticLock(excluded = true)
    @Column(nullable = false)
    private int position;

    @OptimisticLock(excluded = true)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private DealStatus status;

    @OptimisticLock(excluded = true)
    @Column(name = "closed_at")
    private LocalDate closedAt;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "agency_id")
    private UUID agencyId;

    @Column(name = "tender_id")
    private UUID tenderId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal value;

    @Column(nullable = false)
    private int priority;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(length = 2000)
    private String notes;

    protected Deal() {}

    public Deal(UUID pipelineId, DealStage stage, int position, DealDraft draft, LocalDate today) {
        this.id = UUID.randomUUID();
        this.pipelineId = pipelineId;
        update(draft);
        placeAt(stage, position, today);
    }

    public void update(DealDraft d) {
        this.title = Pipeline.text(d.title(), "o título da negociação", 200);
        if (d.agencyId() == null && d.tenderId() == null) {
            throw new DomainRuleViolation(
                    "AGENCY_OR_TENDER_REQUIRED", "Informe o órgão ou a licitação de origem da negociação.");
        }
        this.agencyId = d.agencyId();
        this.tenderId = d.tenderId();
        this.value = new Money(d.value() == null ? BigDecimal.ZERO : d.value()).amount();
        if (d.priority() < MIN_PRIORITY || d.priority() > MAX_PRIORITY) {
            throw new DomainRuleViolation(
                    "INVALID_PRIORITY", "A prioridade vai de " + MIN_PRIORITY + " a " + MAX_PRIORITY + ".");
        }
        this.priority = d.priority();
        this.ownerId = d.ownerId();
        if (d.notes() != null && d.notes().length() > 2000) {
            throw new DomainRuleViolation("TOO_LONG", "Use no máximo 2000 caracteres nas observações.");
        }
        this.notes = d.notes() == null || d.notes().isBlank() ? null : d.notes().trim();
    }

    /**
     * Posição dentro da etapa; quem renumera os vizinhos é o serviço. Entrar numa etapa fechada (ganha ou perdida)
     * registra a data; voltar para uma aberta limpa.
     */
    public void placeAt(DealStage stage, int position, LocalDate today) {
        boolean wasClosed = status != null && status != DealStatus.EM_ANDAMENTO;
        this.stageId = stage.getId();
        this.position = position;
        this.status = stage.getKind().status();
        if (status == DealStatus.EM_ANDAMENTO) {
            this.closedAt = null;
        } else if (!wasClosed || closedAt == null) {
            this.closedAt = today;
        }
    }

    /** Renumeração dentro da mesma etapa (outra negociação entrou ou saiu). */
    public void reposition(int position) {
        this.position = position;
    }

    public boolean isNew(Instant now) {
        return status == DealStatus.EM_ANDAMENTO
                && getCreatedAt() != null
                && getCreatedAt().isAfter(now.minus(NEW_FOR));
    }

    public UUID getId() {
        return id;
    }

    public UUID getPipelineId() {
        return pipelineId;
    }

    public UUID getStageId() {
        return stageId;
    }

    public int getPosition() {
        return position;
    }

    public DealStatus getStatus() {
        return status;
    }

    public LocalDate getClosedAt() {
        return closedAt;
    }

    public String getTitle() {
        return title;
    }

    public UUID getAgencyId() {
        return agencyId;
    }

    public UUID getTenderId() {
        return tenderId;
    }

    public BigDecimal getValue() {
        return value;
    }

    public int getPriority() {
        return priority;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getNotes() {
        return notes;
    }
}
