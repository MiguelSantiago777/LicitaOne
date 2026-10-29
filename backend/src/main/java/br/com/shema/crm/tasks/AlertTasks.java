package br.com.shema.crm.tasks;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * API pública das tarefas para os alertas (ADR 0015). A chave do alerta é única: abrir duas vezes a mesma chave não
 * cria a segunda tarefa.
 */
public interface AlertTasks {

    /** Rascunho de e-mail ao órgão; {@code to} vazio quando o órgão não tem contato com e-mail. */
    record Draft(String to, String subject, String body) {}

    /** @param draft rascunho ao órgão, ou nulo */
    record AlertTask(
            String alertKey,
            String title,
            String notes,
            OffsetDateTime dueAt,
            UUID ownerId,
            UUID tenderId,
            Draft draft) {}

    /** Cria a tarefa do alerta. Devolve o id da tarefa, nova ou a que já existia com a mesma chave. */
    UUID open(AlertTask task);

    /** Conclui a tarefa aberta do alerta, se houver (o fato que a gerou deixou de valer). */
    boolean completeBySystem(String alertKey);

    /** Tarefas abertas com o vencimento já passado, para as métricas. */
    long countOverdue();
}
