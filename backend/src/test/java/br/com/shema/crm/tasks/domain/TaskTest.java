package br.com.shema.crm.tasks.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 29, 10, 0, 0, 0, ZoneOffset.ofHours(-3));

    private static void assertRule(Runnable action, String rule) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DomainRuleViolation.class)
                .extracting(e -> ((DomainRuleViolation) e).rule())
                .isEqualTo(rule);
    }

    private static TaskDraft draft(OffsetDateTime dueAt) {
        return new TaskDraft("  Cobrar   o contrato ", null, dueAt, null, null, null);
    }

    @Test
    void overdueIsComputedFromTheCurrentTime() {
        Task task = Task.manual(draft(NOW.minusMinutes(1)));
        assertThat(task.getTitle()).isEqualTo("Cobrar o contrato");
        assertThat(task.status(NOW)).isEqualTo(TaskStatus.ATRASADA);
        assertThat(task.status(NOW.minusHours(1))).isEqualTo(TaskStatus.ABERTA);

        UUID by = UUID.randomUUID();
        task.complete(by, NOW);
        assertThat(task.status(NOW)).isEqualTo(TaskStatus.CONCLUIDA);
        assertThat(task.isOverdue(NOW)).isFalse();

        // concluir de novo não muda quem nem quando
        task.complete(UUID.randomUUID(), NOW.plusDays(1));
        assertThat(task.getCompletedAt()).isEqualTo(NOW);
        assertThat(task.getCompletedBy()).isEqualTo(by);

        task.reopen();
        assertThat(task.status(NOW)).isEqualTo(TaskStatus.ATRASADA);
        assertRule(task::reopen, "TASK_NOT_COMPLETED");
    }

    @Test
    void titleAndDueDateAreRequired() {
        assertRule(() -> Task.manual(new TaskDraft(" ", null, NOW, null, null, null)), "REQUIRED");
        assertRule(() -> Task.manual(new TaskDraft("Ligar", null, null, null, null, null)), "REQUIRED");
        assertRule(() -> Task.manual(new TaskDraft("x".repeat(201), null, NOW, null, null, null)), "TOO_LONG");
    }

    @Test
    void alertTasksAreNeverDeleted() {
        Task manual = Task.manual(draft(NOW));
        manual.requireDeletable();
        assertThat(manual.getOrigin()).isEqualTo(TaskOrigin.MANUAL);

        Task alert = Task.fromAlert(draft(NOW), "contrato-sem-pedido:abc:2026-10");
        assertThat(alert.getOrigin()).isEqualTo(TaskOrigin.ALERTA);
        assertRule(alert::requireDeletable, "ALERT_TASK_NOT_DELETABLE");
    }
}
