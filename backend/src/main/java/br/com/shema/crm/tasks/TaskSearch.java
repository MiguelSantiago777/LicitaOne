package br.com.shema.crm.tasks;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** API pública de busca de tarefas (Fase 11: o assistente de perguntas). */
public interface TaskSearch {

    /**
     * Tudo opcional.
     *
     * @param situation ABERTAS (atrasadas incluídas), ATRASADAS, CONCLUIDAS ou TODAS; desconhecida vale ABERTAS
     * @param dueUntil só as que vencem até esta hora
     */
    record Query(String situation, UUID ownerId, UUID tenderId, OffsetDateTime dueUntil, String text) {}

    /** @param status ABERTA, ATRASADA ou CONCLUIDA */
    record Row(
            UUID id,
            String title,
            OffsetDateTime dueAt,
            String status,
            String ownerName,
            UUID tenderId,
            String tenderLabel,
            UUID dealId,
            String dealTitle) {}

    /** @param total quantas passam no filtro (as linhas vêm no máximo {@code limit}, das que vencem primeiro) */
    record Result(List<Row> rows, long total) {}

    Result search(Query query, int limit);
}
