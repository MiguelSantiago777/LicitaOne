package br.com.shema.crm.notifications.infrastructure;

import br.com.shema.crm.notifications.domain.Alert;
import br.com.shema.crm.notifications.domain.AlertRule;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * O que já foi avisado (tabela {@code alerts}). A chave é única: gravar uma chave que já existe não faz nada, e o job
 * só publica o alerta quando a gravação é nova. Os consumidores anotam a tarefa criada e o aviso mandado.
 */
@Component
public class AlertLog {

    /** @param notifiedTo para quem foi o aviso interno, ou nulo se ainda não foi */
    public record Row(
            UUID id,
            AlertRule rule,
            UUID tenderId,
            UUID subjectId,
            String agencyName,
            String title,
            LocalDate dueOn,
            UUID ownerId,
            Instant raisedAt,
            UUID taskId,
            Instant taskOpenedAt,
            String notifiedTo,
            Instant notifiedAt) {}

    private final NamedParameterJdbcTemplate jdbc;

    public AlertLog(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Grava o alerta se a chave for nova e devolve o id; vazio se a chave já tinha sido avisada. */
    public Optional<UUID> recordIfNew(Alert alert, Instant at) {
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update(
                """
                insert into alerts (id, alert_key, rule, tender_id, subject_id, agency_name, title, due_on, owner_id,
                                    raised_at)
                values (:id, :key, :rule, :tender, :subject, :agency, :title, :due, :owner, :at)
                on conflict (alert_key) do nothing
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("key", alert.key())
                        .addValue("rule", alert.rule().name())
                        .addValue("tender", alert.tenderId())
                        .addValue("subject", alert.subjectId())
                        .addValue("agency", truncate(alert.agencyName(), 200))
                        .addValue("title", alert.title())
                        .addValue("due", alert.dueOn())
                        .addValue("owner", alert.ownerId())
                        .addValue("at", Timestamp.from(at)));
        return inserted == 1 ? Optional.of(id) : Optional.empty();
    }

    public void taskOpened(UUID alertId, UUID taskId, Instant at) {
        jdbc.update(
                "update alerts set task_id = :task, task_opened_at = coalesce(task_opened_at, :at) where id = :id",
                new MapSqlParameterSource()
                        .addValue("id", alertId)
                        .addValue("task", taskId)
                        .addValue("at", Timestamp.from(at)));
    }

    public void notified(UUID alertId, String to, Instant at) {
        jdbc.update(
                "update alerts set notified_to = :to, notified_at = :at where id = :id",
                new MapSqlParameterSource()
                        .addValue("id", alertId)
                        .addValue("to", truncate(to, 1000))
                        .addValue("at", Timestamp.from(at)));
    }

    /** Os mais recentes primeiro. */
    public List<Row> page(AlertRule rule, int page, int size) {
        return jdbc.query(
                "select * from alerts where (cast(:rule as varchar) is null or rule = :rule)"
                        + " order by raised_at desc, title limit :size offset :offset",
                new MapSqlParameterSource()
                        .addValue("rule", rule == null ? null : rule.name())
                        .addValue("size", size)
                        .addValue("offset", (long) page * size),
                AlertLog::row);
    }

    public long count(AlertRule rule) {
        Long count = jdbc.queryForObject(
                "select count(*) from alerts where (cast(:rule as varchar) is null or rule = :rule)",
                new MapSqlParameterSource("rule", rule == null ? null : rule.name()),
                Long.class);
        return count == null ? 0 : count;
    }

    /** Quando o job (agendado ou "verificar agora") rodou pela última vez, pelo lock do ShedLock. */
    public Optional<Instant> lastRun(String lockName) {
        return jdbc
                .query(
                        // locked_at é a hora UTC sem fuso (ShedLock com a hora do banco, V15)
                        "select locked_at at time zone 'UTC' as locked_at from shedlock where name = :name",
                        new MapSqlParameterSource("name", lockName),
                        (rs, n) ->
                                rs.getObject("locked_at", OffsetDateTime.class).toInstant())
                .stream()
                .findFirst();
    }

    private static Row row(ResultSet rs, int n) throws SQLException {
        return new Row(
                rs.getObject("id", UUID.class),
                AlertRule.valueOf(rs.getString("rule")),
                rs.getObject("tender_id", UUID.class),
                rs.getObject("subject_id", UUID.class),
                rs.getString("agency_name"),
                rs.getString("title"),
                rs.getObject("due_on", LocalDate.class),
                rs.getObject("owner_id", UUID.class),
                instant(rs, "raised_at"),
                rs.getObject("task_id", UUID.class),
                instant(rs, "task_opened_at"),
                rs.getString("notified_to"),
                instant(rs, "notified_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String truncate(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }
}
