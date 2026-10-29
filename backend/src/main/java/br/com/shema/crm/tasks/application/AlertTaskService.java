package br.com.shema.crm.tasks.application;

import br.com.shema.crm.tasks.AlertTasks;
import br.com.shema.crm.tasks.domain.EmailDraft;
import br.com.shema.crm.tasks.domain.Task;
import br.com.shema.crm.tasks.domain.TaskDraft;
import br.com.shema.crm.tasks.infrastructure.TaskRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tarefas criadas e concluídas pelos alertas, sem usuário logado (rodam no consumidor da fila). */
@Service
@Transactional
class AlertTaskService implements AlertTasks {

    private final TaskRepository tasks;
    private final Clock clock;

    AlertTaskService(TaskRepository tasks, Clock clock) {
        this.tasks = tasks;
        this.clock = clock;
    }

    @Override
    public UUID open(AlertTask alert) {
        return tasks.findByAlertKey(alert.alertKey())
                .map(Task::getId)
                .orElseGet(() -> tasks.saveAndFlush(Task.fromAlert(
                                new TaskDraft(
                                        alert.title(),
                                        alert.notes(),
                                        alert.dueAt(),
                                        alert.ownerId(),
                                        null,
                                        alert.tenderId()),
                                alert.alertKey(),
                                alert.draft() == null
                                        ? null
                                        : new EmailDraft(
                                                alert.draft().to(),
                                                alert.draft().subject(),
                                                alert.draft().body())))
                        .getId());
    }

    @Override
    public boolean completeBySystem(String alertKey) {
        return tasks.findByAlertKey(alertKey)
                .map(task -> task.completeBySystem(OffsetDateTime.now(clock)))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public long countOverdue() {
        Object[] row = tasks.countOpen(null, OffsetDateTime.now(clock)).getFirst();
        return ((Number) row[1]).longValue();
    }
}
