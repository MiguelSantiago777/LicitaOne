package br.com.shema.crm.deals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** API pública de busca de negociações do funil (Fase 11: o assistente de perguntas). */
public interface DealSearch {

    /** @param statuses EM_ANDAMENTO, GANHA ou PERDIDA; vazio, todas */
    record Query(Set<String> statuses, UUID ownerId, UUID agencyId, String text) {}

    record Row(
            UUID id,
            String title,
            String stageName,
            String status,
            BigDecimal value,
            String ownerName,
            String agencyName,
            UUID tenderId,
            int openTasks,
            boolean hasOverdueTask,
            LocalDate closedAt) {}

    List<Row> search(Query query);
}
