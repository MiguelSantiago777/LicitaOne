package br.com.shema.crm.assistant.infrastructure;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Contagem diária de perguntas por pessoa (V19). Só o número: o texto das perguntas não é guardado. */
@Repository
public class AssistantUsageRepository {

    private final JdbcClient jdbc;

    AssistantUsageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public int usedOn(UUID userId, LocalDate day) {
        return jdbc.sql("select questions from assistant_usage where user_id = :user and day = :day")
                .param("user", userId)
                .param("day", day)
                .query(Integer.class)
                .optional()
                .orElse(0);
    }

    /** Soma uma pergunta e devolve o total do dia, numa instrução só (duas abas ao mesmo tempo não escapam). */
    @Transactional
    public int increment(UUID userId, LocalDate day) {
        return jdbc.sql("""
                        insert into assistant_usage (user_id, day, questions) values (:user, :day, 1)
                        on conflict (user_id, day) do update set questions = assistant_usage.questions + 1
                        returning questions
                        """)
                .param("user", userId)
                .param("day", day)
                .query(Integer.class)
                .single();
    }

    /** Devolve a pergunta que não chegou a rodar (limite estourado ou fila cheia). */
    @Transactional
    public void decrement(UUID userId, LocalDate day) {
        jdbc.sql("update assistant_usage set questions = questions - 1"
                        + " where user_id = :user and day = :day and questions > 0")
                .param("user", userId)
                .param("day", day)
                .update();
    }
}
