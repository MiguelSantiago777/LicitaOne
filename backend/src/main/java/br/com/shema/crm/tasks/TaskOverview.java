package br.com.shema.crm.tasks;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** API pública das tarefas para os outros módulos: o card da negociação mostra as abertas e a atrasada mais antiga. */
public interface TaskOverview {

    /**
     * @param open tarefas abertas (atrasadas incluídas)
     * @param overdueTitle título da atrasada mais antiga, ou nulo se nenhuma está atrasada
     */
    record OpenTasks(int open, int overdue, String overdueTitle, OffsetDateTime overdueDueAt) {}

    /** Só aparecem no mapa as negociações com pelo menos uma tarefa aberta. */
    Map<UUID, OpenTasks> openByDeal(Collection<UUID> dealIds);

    /** Uma negociação com tarefa aberta não pode ser apagada. */
    long countOpenByDeal(UUID dealId);
}
