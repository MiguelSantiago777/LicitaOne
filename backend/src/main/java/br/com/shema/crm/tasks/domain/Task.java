package br.com.shema.crm.tasks.domain;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tarefa com vencimento (data e hora) e responsável, ligada opcionalmente a uma negociação e a uma licitação. A chave
 * do alerta é única: o mesmo alerta nunca gera duas tarefas (Fase 5).
 */
@Entity
@Table(name = "tasks")
public class Task extends AuditableEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String notes;

    @Column(name = "due_at", nullable = false)
    private OffsetDateTime dueAt;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "deal_id")
    private UUID dealId;

    @Column(name = "tender_id")
    private UUID tenderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private TaskOrigin origin;

    @Column(name = "alert_key", length = 120, updatable = false)
    private String alertKey;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "completed_by")
    private UUID completedBy;

    protected Task() {}

    public static Task manual(TaskDraft draft) {
        return new Task(draft, TaskOrigin.MANUAL, null);
    }

    public static Task fromAlert(TaskDraft draft, String alertKey) {
        if (alertKey == null || alertKey.isBlank()) {
            throw new IllegalArgumentException("Tarefa de alerta precisa da chave de idempotência");
        }
        return new Task(draft, TaskOrigin.ALERTA, alertKey);
    }

    private Task(TaskDraft draft, TaskOrigin origin, String alertKey) {
        this.id = UUID.randomUUID();
        this.origin = origin;
        this.alertKey = alertKey;
        update(draft);
    }

    public void update(TaskDraft d) {
        this.title = text(d.title());
        if (d.notes() != null && d.notes().length() > 2000) {
            throw new DomainRuleViolation("TOO_LONG", "Use no máximo 2000 caracteres nas observações.");
        }
        this.notes = d.notes() == null || d.notes().isBlank() ? null : d.notes().trim();
        if (d.dueAt() == null) {
            throw new DomainRuleViolation("REQUIRED", "Informe a data e a hora da tarefa.");
        }
        this.dueAt = d.dueAt();
        this.ownerId = d.ownerId();
        this.dealId = d.dealId();
        this.tenderId = d.tenderId();
    }

    /** Concluir de novo não muda nada: a conclusão em massa pode incluir uma tarefa já concluída. */
    public void complete(UUID by, OffsetDateTime at) {
        if (completedAt == null) {
            this.completedAt = at;
            this.completedBy = by;
        }
    }

    public void reopen() {
        if (completedAt == null) {
            throw new DomainRuleViolation("TASK_NOT_COMPLETED", "A tarefa já está aberta.");
        }
        this.completedAt = null;
        this.completedBy = null;
    }

    /** Tarefa de alerta não é apagada: some a chave e o próximo alerta criaria de novo. Conclua em vez disso. */
    public void requireDeletable() {
        if (origin == TaskOrigin.ALERTA) {
            throw new DomainRuleViolation(
                    "ALERT_TASK_NOT_DELETABLE", "Tarefa criada por alerta não pode ser apagada. Conclua a tarefa.");
        }
    }

    public TaskStatus status(OffsetDateTime now) {
        if (completedAt != null) {
            return TaskStatus.CONCLUIDA;
        }
        return dueAt.isBefore(now) ? TaskStatus.ATRASADA : TaskStatus.ABERTA;
    }

    public boolean isOverdue(OffsetDateTime now) {
        return status(now) == TaskStatus.ATRASADA;
    }

    private static String text(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleViolation("REQUIRED", "Informe o título da tarefa.");
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        if (trimmed.length() > 200) {
            throw new DomainRuleViolation("TOO_LONG", "Use no máximo 200 caracteres no título da tarefa.");
        }
        return trimmed;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getNotes() {
        return notes;
    }

    public OffsetDateTime getDueAt() {
        return dueAt;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getDealId() {
        return dealId;
    }

    public UUID getTenderId() {
        return tenderId;
    }

    public TaskOrigin getOrigin() {
        return origin;
    }

    public String getAlertKey() {
        return alertKey;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public UUID getCompletedBy() {
        return completedBy;
    }
}
