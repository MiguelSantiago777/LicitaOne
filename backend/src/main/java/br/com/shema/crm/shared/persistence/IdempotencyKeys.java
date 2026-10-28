package br.com.shema.crm.shared.persistence;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Idempotency-Key nos POSTs críticos (pedido, importação): a mesma chave, do mesmo usuário e na mesma operação,
 * devolve o recurso criado da primeira vez em vez de criar outro. A gravação entra na transação do chamador; se
 * duas requisições com a mesma chave correrem juntas, a chave única do banco faz a segunda falhar.
 */
@Component
public class IdempotencyKeys {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public IdempotencyKeys(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Optional<UUID> find(String scope, String key, UUID userId) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        List<UUID> found = jdbc.queryForList(
                "select resource_id from idempotency_keys where scope = ? and key = ? and user_id = ?",
                UUID.class,
                scope,
                key,
                userId);
        return found.stream().findFirst();
    }

    public void remember(String scope, String key, UUID userId, UUID resourceId) {
        if (key == null || key.isBlank()) {
            return;
        }
        jdbc.update(
                "insert into idempotency_keys (scope, key, user_id, resource_id, created_at) values (?, ?, ?, ?, ?)",
                scope,
                key,
                userId,
                resourceId,
                java.sql.Timestamp.from(clock.instant()));
    }
}
