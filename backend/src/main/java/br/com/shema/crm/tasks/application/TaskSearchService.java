package br.com.shema.crm.tasks.application;

import br.com.shema.crm.tasks.TaskSearch;
import java.util.Arrays;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class TaskSearchService implements TaskSearch {

    /** Quantas tarefas olhar antes de filtrar pelo vencimento: a lista é pequena (dezenas ou centenas). */
    private static final int SCAN = 500;

    private final TaskService tasks;

    TaskSearchService(TaskService tasks) {
        this.tasks = tasks;
    }

    @Override
    public Result search(Query q, int limit) {
        TaskFilter.Situation situation = Arrays.stream(TaskFilter.Situation.values())
                .filter(s -> s.name().equals(q.situation()))
                .findFirst()
                .orElse(TaskFilter.Situation.ABERTAS);
        Page<TaskView> page = tasks.search(
                new TaskFilter(situation, q.text(), q.ownerId(), null, q.tenderId()),
                PageRequest.of(0, SCAN, Sort.by("dueAt").and(Sort.by("id"))));
        List<TaskView> matching = page.getContent().stream()
                .filter(t ->
                        q.dueUntil() == null || (t.dueAt() != null && !t.dueAt().isAfter(q.dueUntil())))
                .toList();
        return new Result(
                matching.stream()
                        .limit(Math.clamp(limit, 1, 100))
                        .map(t -> new Row(
                                t.id(),
                                t.title(),
                                t.dueAt(),
                                t.status().name(),
                                t.owner() == null ? null : t.owner().name(),
                                t.tender() == null ? null : t.tender().id(),
                                t.tender() == null
                                        ? null
                                        : t.tender().agencyName() + " · "
                                                + t.tender().identification(),
                                t.deal() == null ? null : t.deal().id(),
                                t.deal() == null ? null : t.deal().title()))
                        .toList(),
                q.dueUntil() == null ? page.getTotalElements() : matching.size());
    }
}
