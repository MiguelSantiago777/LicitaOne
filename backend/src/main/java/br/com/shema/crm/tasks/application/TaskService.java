package br.com.shema.crm.tasks.application;

import br.com.shema.crm.identity.UserDirectory;
import br.com.shema.crm.identity.UserSummary;
import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.security.CurrentUser;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import br.com.shema.crm.tasks.TaskOverview;
import br.com.shema.crm.tasks.TaskSubjects;
import br.com.shema.crm.tasks.TaskSubjects.DealSubject;
import br.com.shema.crm.tasks.domain.Task;
import br.com.shema.crm.tasks.domain.TaskDraft;
import br.com.shema.crm.tasks.infrastructure.TaskRepository;
import br.com.shema.crm.tenders.TenderLifecycle;
import br.com.shema.crm.tenders.TenderRef;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso das tarefas. Qualquer ADMIN ou COMERCIAL cria (inclusive para outra pessoa); alterar, concluir e apagar
 * seguem a regra das licitações: ADMIN tudo, COMERCIAL as suas e as sem responsável.
 */
@Service
@Transactional
public class TaskService implements TaskOverview {

    public static final int MAX_BULK = 200;

    public record Summary(long open, long overdue) {}

    private final TaskRepository tasks;
    private final UserDirectory users;
    private final TenderLifecycle tenders;
    private final TaskSubjects subjects;
    private final Clock clock;

    public TaskService(
            TaskRepository tasks, UserDirectory users, TenderLifecycle tenders, TaskSubjects subjects, Clock clock) {
        this.tasks = tasks;
        this.users = users;
        this.tenders = tenders;
        this.subjects = subjects;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<TaskView> search(TaskFilter filter, Pageable pageable) {
        OffsetDateTime now = now();
        Page<Task> page = tasks.findAll(TaskSpecifications.matching(filter, now), pageable);
        return new PageImpl<>(views(page.getContent(), now), pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID ownerId) {
        Object[] row = tasks.countOpen(ownerId, now()).getFirst();
        return new Summary(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
    }

    @Transactional(readOnly = true)
    public TaskView get(UUID id) {
        return view(load(id));
    }

    public TaskView create(TaskDraft draft) {
        CurrentUser.get().requireCanEditOwnedBy(null);
        validateLinks(draft);
        Task task = tasks.saveAndFlush(Task.manual(draft));
        return view(task);
    }

    public TaskView update(UUID id, long version, TaskDraft draft) {
        Task task = editable(id, version);
        validateLinks(draft);
        task.update(draft);
        tasks.flush();
        return view(task);
    }

    /** Tudo ou nada: se uma das tarefas não puder ser alterada por quem pediu, nenhuma é concluída. */
    public int complete(Collection<UUID> ids) {
        Set<UUID> unique = new LinkedHashSet<>(ids);
        if (unique.isEmpty()) {
            return 0;
        }
        if (unique.size() > MAX_BULK) {
            throw new DomainRuleViolation("TOO_MANY_TASKS", "Conclua no máximo " + MAX_BULK + " tarefas de uma vez.");
        }
        List<Task> found = tasks.findAllById(unique);
        if (found.size() != unique.size()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Uma das tarefas não existe mais. Recarregue a lista.");
        }
        CurrentUser me = CurrentUser.get();
        for (Task task : found) {
            if (!me.canEditOwnedBy(task.getOwnerId())) {
                throw new ApiException(
                        ErrorCode.ACCESS_DENIED,
                        "A tarefa \"" + task.getTitle() + "\" é de outra pessoa. Nenhuma tarefa foi concluída.");
            }
        }
        OffsetDateTime now = now();
        int completed = 0;
        for (Task task : found) {
            if (task.getCompletedAt() == null) {
                task.complete(me.id(), now);
                completed++;
            }
        }
        tasks.flush();
        return completed;
    }

    public TaskView reopen(UUID id, long version) {
        Task task = editable(id, version);
        task.reopen();
        tasks.flush();
        return view(task);
    }

    public void delete(UUID id, long version) {
        Task task = editable(id, version);
        task.requireDeletable();
        tasks.delete(task);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, OpenTasks> openByDeal(Collection<UUID> dealIds) {
        if (dealIds.isEmpty()) {
            return Map.of();
        }
        OffsetDateTime now = now();
        Map<UUID, List<Task>> byDeal =
                tasks.findOpenByDealIds(dealIds).stream().collect(Collectors.groupingBy(Task::getDealId));
        Map<UUID, OpenTasks> result = new HashMap<>();
        byDeal.forEach((dealId, open) -> {
            // Já vêm ordenadas pelo vencimento: a primeira atrasada é a mais antiga
            List<Task> overdue = open.stream().filter(t -> t.isOverdue(now)).toList();
            Task oldest = overdue.isEmpty() ? null : overdue.getFirst();
            result.put(
                    dealId,
                    new OpenTasks(
                            open.size(),
                            overdue.size(),
                            oldest == null ? null : oldest.getTitle(),
                            oldest == null ? null : oldest.getDueAt()));
        });
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public long countOpenByDeal(UUID dealId) {
        return tasks.countByDealIdAndCompletedAtIsNull(dealId);
    }

    private Task load(UUID id) {
        return tasks.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Tarefa não encontrada."));
    }

    private Task editable(UUID id, long version) {
        Task task = load(id);
        CurrentUser.get().requireCanEditOwnedBy(task.getOwnerId());
        if (task.getVersion() == null || task.getVersion() != version) {
            throw new ApiException(
                    ErrorCode.VERSION_CONFLICT,
                    "Outra pessoa alterou esta tarefa enquanto você editava. Recarregue para ver a versão atual.");
        }
        return task;
    }

    private void validateLinks(TaskDraft draft) {
        if (draft.ownerId() != null && users.find(draft.ownerId()).isEmpty()) {
            throw new DomainRuleViolation("OWNER_NOT_FOUND", "Responsável não encontrado.");
        }
        if (draft.dealId() != null && subjects.findDeal(draft.dealId()).isEmpty()) {
            throw new DomainRuleViolation("DEAL_NOT_FOUND", "Negociação não encontrada.");
        }
        if (draft.tenderId() != null && tenders.find(draft.tenderId()).isEmpty()) {
            throw new DomainRuleViolation("TENDER_NOT_FOUND", "Licitação não encontrada.");
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private TaskView view(Task task) {
        return views(List.of(task), now()).getFirst();
    }

    /** Responsáveis, negociações e licitações buscados em lote: uma consulta de cada, não uma por linha. */
    private List<TaskView> views(List<Task> page, OffsetDateTime now) {
        Map<UUID, UserSummary> owners = users.findAll(ids(page, Task::getOwnerId));
        Map<UUID, DealSubject> deals = subjects.findDeals(ids(page, Task::getDealId));
        Map<UUID, TenderRef> linked = tenders.findAll(ids(page, Task::getTenderId));
        CurrentUser me = CurrentUser.get();
        List<TaskView> result = new ArrayList<>(page.size());
        for (Task t : page) {
            UserSummary owner = lookup(owners, t.getOwnerId());
            DealSubject deal = lookup(deals, t.getDealId());
            TenderRef tender = lookup(linked, t.getTenderId());
            result.add(new TaskView(
                    t.getId(),
                    t.getTitle(),
                    t.getNotes(),
                    t.getDueAt(),
                    t.status(now),
                    owner == null ? null : new TaskView.Owner(owner.id(), owner.name(), owner.initials()),
                    deal == null
                            ? null
                            : new TaskView.DealLink(deal.id(), deal.title(), deal.agencyName(), deal.value()),
                    tender == null
                            ? null
                            : new TaskView.TenderLink(
                                    tender.id(), tender.agencyName(), tender.identification(), tender.totalValue()),
                    deal != null ? deal.value() : tender != null ? tender.totalValue() : null,
                    t.getOrigin(),
                    t.getCompletedAt(),
                    t.getVersion() == null ? 0 : t.getVersion(),
                    me.canEditOwnedBy(t.getOwnerId())));
        }
        return result;
    }

    private static Set<UUID> ids(List<Task> page, Function<Task, UUID> id) {
        return page.stream().map(id).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /** Mapa vazio pode ser {@code Map.of()}, que não aceita {@code get(null)}; tarefa sem vínculo é normal. */
    private static <T> T lookup(Map<UUID, T> map, UUID id) {
        return id == null ? null : map.get(id);
    }
}
