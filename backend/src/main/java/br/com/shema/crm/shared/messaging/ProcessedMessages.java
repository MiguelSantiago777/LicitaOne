package br.com.shema.crm.shared.messaging;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Mensagens já processadas por consumidor: a mesma mensagem entregue duas vezes só tem efeito uma vez. */
@Component
public class ProcessedMessages {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ProcessedMessages(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Marca como processada; devolve false se já tinha sido. Deve rodar na transação do efeito. */
    public boolean markIfNew(UUID messageId, String consumer) {
        return jdbc.update(
                        "insert into processed_messages (message_id, consumer, processed_at) values (?, ?, ?)"
                                + " on conflict do nothing",
                        messageId,
                        consumer,
                        Timestamp.from(clock.instant()))
                == 1;
    }
}
