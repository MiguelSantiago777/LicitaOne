package br.com.shema.crm.deals.application;

import br.com.shema.crm.deals.domain.Deal;
import br.com.shema.crm.deals.domain.DealDraft;
import br.com.shema.crm.deals.domain.DealStage;
import br.com.shema.crm.deals.domain.Pipeline;
import br.com.shema.crm.deals.domain.StageKind;
import br.com.shema.crm.deals.infrastructure.DealRepository;
import br.com.shema.crm.deals.infrastructure.PipelineRepository;
import br.com.shema.crm.identity.UserDirectory;
import br.com.shema.crm.identity.UserSummary;
import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.security.CurrentUser;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import br.com.shema.crm.tasks.TaskOverview;
import br.com.shema.crm.tasks.TaskOverview.OpenTasks;
import br.com.shema.crm.tenders.AgencyDirectory;
import br.com.shema.crm.tenders.AgencyRef;
import br.com.shema.crm.tenders.TenderLifecycle;
import br.com.shema.crm.tenders.TenderRef;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso do funil. Etapas: só ADMIN, com a versão do funil. Negociações: ADMIN altera todas, COMERCIAL as suas e
 * as sem responsável (a regra das licitações). Mover trava o funil só durante a renumeração, como nos quadros.
 *
 * <p>O filtro roda em memória sobre as negociações do funil: o funil tem centenas de negociações, não milhares, e assim
 * a busca alcança o nome do órgão, que mora em outro módulo (ADR 0013).
 */
@Service
@Transactional
public class DealService {

    private final PipelineRepository pipelines;
    private final DealRepository deals;
    private final UserDirectory users;
    private final AgencyDirectory agencies;
    private final TenderLifecycle tenders;
    private final TaskOverview tasks;
    private final EntityManager entityManager;
    private final Clock clock;

    public DealService(
            PipelineRepository pipelines,
            DealRepository deals,
            UserDirectory users,
            AgencyDirectory agencies,
            TenderLifecycle tenders,
            TaskOverview tasks,
            EntityManager entityManager,
            Clock clock) {
        this.pipelines = pipelines;
        this.deals = deals;
        this.users = users;
        this.agencies = agencies;
        this.tenders = tenders;
        this.tasks = tasks;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PipelineView pipeline(DealFilter filter) {
        return view(pipeline(), filter);
    }

    @Transactional(readOnly = true)
    public PipelineView.Deal get(UUID dealId) {
        Deal deal = load(dealId);
        return views(List.of(deal), pipeline()).getFirst();
    }

    // ---- etapas (ADMIN) ----

    public PipelineView addStage(long version, String name, StageKind kind) {
        Pipeline pipeline = structureChange(version);
        pipeline.addStage(name, kind == null ? StageKind.ABERTA : kind);
        pipelines.flush();
        return view(pipeline, DealFilter.NONE);
    }

    public PipelineView renameStage(long version, UUID stageId, String name) {
        Pipeline pipeline = structureChange(version);
        pipeline.renameStage(stageId, name);
        pipelines.flush();
        return view(pipeline, DealFilter.NONE);
    }

    public PipelineView moveStage(long version, UUID stageId, int position) {
        Pipeline pipeline = structureChange(version);
        pipeline.moveStage(stageId, position);
        pipelines.flush();
        return view(pipeline, DealFilter.NONE);
    }

    public PipelineView removeStage(long version, UUID stageId) {
        Pipeline pipeline = structureChange(version);
        pipeline.removeStage(stageId, deals.countByStageId(stageId));
        pipelines.flush();
        return view(pipeline, DealFilter.NONE);
    }

    // ---- negociações ----

    /** Sem etapa escolhida, entra na primeira etapa aberta. Sempre no fim da etapa. */
    public PipelineView.Deal create(UUID stageId, DealDraft draft) {
        CurrentUser.get().requireCanEditOwnedBy(null);
        Pipeline pipeline = pipeline();
        DealStage stage = stageId == null ? pipeline.firstOpenStage() : pipeline.stage(stageId);
        validateLinks(draft);
        entityManager.lock(pipeline, LockModeType.PESSIMISTIC_WRITE);
        int position = (int) deals.countByStageId(stage.getId());
        Deal deal = deals.saveAndFlush(new Deal(pipeline.getId(), stage, position, draft, today()));
        return views(List.of(deal), pipeline).getFirst();
    }

    public PipelineView.Deal update(UUID dealId, long version, DealDraft draft) {
        Deal deal = editable(dealId, version);
        validateLinks(draft);
        deal.update(draft);
        deals.flush();
        return views(List.of(deal), pipeline()).getFirst();
    }

    /** Leva a negociação para a etapa e a posição (começa em 0), renumerando a etapa de origem e a de destino. */
    public PipelineView.Deal move(UUID dealId, UUID stageId, int position) {
        Deal deal = load(dealId);
        CurrentUser.get().requireCanEditOwnedBy(deal.getOwnerId());
        Pipeline pipeline = pipeline();
        DealStage target = pipeline.stage(stageId);
        entityManager.lock(pipeline, LockModeType.PESSIMISTIC_WRITE);

        UUID from = deal.getStageId();
        List<Deal> column = new ArrayList<>(deals.findByStageIdOrderByPosition(stageId));
        column.removeIf(d -> d.getId().equals(dealId));
        column.add(Math.clamp(position, 0, column.size()), deal);
        LocalDate today = today();
        for (int i = 0; i < column.size(); i++) {
            Deal d = column.get(i);
            if (d == deal) {
                d.placeAt(target, i, today);
            } else {
                d.reposition(i);
            }
        }
        if (!from.equals(stageId)) {
            renumber(from, dealId);
        }
        deals.flush();
        return views(List.of(deal), pipeline).getFirst();
    }

    /** Negociação com tarefa aberta não sai: a tarefa ficaria sem contexto. As concluídas perdem o vínculo. */
    public void delete(UUID dealId, long version) {
        Deal deal = editable(dealId, version);
        long open = tasks.countOpenByDeal(dealId);
        if (open > 0) {
            throw new DomainRuleViolation(
                    "DEAL_HAS_OPEN_TASKS",
                    "A negociação tem " + open + (open == 1 ? " tarefa aberta" : " tarefas abertas")
                            + ". Conclua antes de apagar.");
        }
        entityManager.lock(pipeline(), LockModeType.PESSIMISTIC_WRITE);
        deals.delete(deal);
        deals.flush();
        renumber(deal.getStageId(), dealId);
        deals.flush();
    }

    private void renumber(UUID stageId, UUID leaving) {
        List<Deal> rest = new ArrayList<>(deals.findByStageIdOrderByPosition(stageId));
        rest.removeIf(d -> d.getId().equals(leaving));
        for (int i = 0; i < rest.size(); i++) {
            rest.get(i).reposition(i);
        }
    }

    private Pipeline pipeline() {
        return pipelines
                .findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Funil de vendas não encontrado."));
    }

    private Deal load(UUID dealId) {
        return deals.findById(dealId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Negociação não encontrada."));
    }

    private Deal editable(UUID dealId, long version) {
        Deal deal = load(dealId);
        CurrentUser.get().requireCanEditOwnedBy(deal.getOwnerId());
        if (deal.getVersion() == null || deal.getVersion() != version) {
            throw new ApiException(
                    ErrorCode.VERSION_CONFLICT,
                    "Outra pessoa alterou esta negociação enquanto você editava. Recarregue para ver a versão atual.");
        }
        return deal;
    }

    /** Etapas ficam em tabela própria: a versão do funil sobe na hora para a resposta já sair com ela. */
    private Pipeline structureChange(long version) {
        if (!CurrentUser.get().isAdmin()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Só um administrador altera as etapas do funil.");
        }
        Pipeline pipeline = pipeline();
        if (pipeline.getVersion() == null || pipeline.getVersion() != version) {
            throw new ApiException(
                    ErrorCode.VERSION_CONFLICT,
                    "Outra pessoa alterou as etapas enquanto você editava. Recarregue para ver a versão atual.");
        }
        entityManager.lock(pipeline, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        return pipeline;
    }

    private void validateLinks(DealDraft draft) {
        if (draft.ownerId() != null && users.find(draft.ownerId()).isEmpty()) {
            throw new DomainRuleViolation("OWNER_NOT_FOUND", "Responsável não encontrado.");
        }
        if (draft.agencyId() != null && agencies.find(draft.agencyId()).isEmpty()) {
            throw new DomainRuleViolation("AGENCY_NOT_FOUND", "Órgão não encontrado.");
        }
        if (draft.tenderId() != null && tenders.find(draft.tenderId()).isEmpty()) {
            throw new DomainRuleViolation("TENDER_NOT_FOUND", "Licitação não encontrada.");
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private PipelineView view(Pipeline pipeline, DealFilter filter) {
        List<PipelineView.Deal> all = views(deals.findByPipelineIdOrderByPosition(pipeline.getId()), pipeline).stream()
                .filter(d -> matches(d, filter))
                .toList();
        Map<UUID, List<PipelineView.Deal>> byStage =
                all.stream().collect(Collectors.groupingBy(PipelineView.Deal::stageId));
        List<PipelineView.Stage> stages = new ArrayList<>();
        for (DealStage stage : pipeline.getStages()) {
            List<PipelineView.Deal> inStage = byStage.getOrDefault(stage.getId(), List.of());
            stages.add(new PipelineView.Stage(
                    stage.getId(),
                    stage.getName(),
                    stage.getPosition(),
                    stage.getKind(),
                    inStage.size(),
                    sum(inStage),
                    inStage));
        }
        CurrentUser me = CurrentUser.get();
        return new PipelineView(
                pipeline.getId(),
                pipeline.getName(),
                pipeline.getVersion() == null ? 0 : pipeline.getVersion(),
                me.isAdmin(),
                me.canEditOwnedBy(null),
                all.size(),
                sum(all),
                stages);
    }

    private static BigDecimal sum(List<PipelineView.Deal> deals) {
        return deals.stream().map(PipelineView.Deal::value).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static boolean matches(PipelineView.Deal d, DealFilter f) {
        if (f.statuses() != null && !f.statuses().isEmpty() && !f.statuses().contains(d.status())) {
            return false;
        }
        if (f.ownerId() != null
                && (d.owner() == null || !f.ownerId().equals(d.owner().id()))) {
            return false;
        }
        if (f.agencyId() != null
                && (d.agency() == null || !f.agencyId().equals(d.agency().id()))) {
            return false;
        }
        if (f.tenderId() != null
                && (d.tender() == null || !f.tenderId().equals(d.tender().id()))) {
            return false;
        }
        if (f.q() != null && !f.q().isBlank()) {
            String q = f.q().trim().toLowerCase(Locale.ROOT);
            return contains(d.title(), q)
                    || (d.agency() != null && contains(d.agency().name(), q))
                    || (d.agency() != null && contains(d.agency().city(), q))
                    || (d.tender() != null && contains(d.tender().agencyName(), q))
                    || (d.tender() != null && contains(d.tender().identification(), q));
        }
        return true;
    }

    private static boolean contains(String text, String q) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(q);
    }

    /** Responsáveis, órgãos, licitações e tarefas buscados em lote: uma consulta de cada, não uma por negociação. */
    List<PipelineView.Deal> views(List<Deal> list, Pipeline pipeline) {
        Map<UUID, UserSummary> owners = users.findAll(ids(list, Deal::getOwnerId));
        Map<UUID, AgencyRef> linkedAgencies = agencies.findAll(ids(list, Deal::getAgencyId));
        Map<UUID, TenderRef> linkedTenders = tenders.findAll(ids(list, Deal::getTenderId));
        Map<UUID, OpenTasks> open = tasks.openByDeal(ids(list, Deal::getId));
        Map<UUID, DealStage> stages =
                pipeline.getStages().stream().collect(Collectors.toMap(DealStage::getId, Function.identity()));
        CurrentUser me = CurrentUser.get();
        Instant now = clock.instant();
        List<PipelineView.Deal> result = new ArrayList<>(list.size());
        for (Deal d : list) {
            UserSummary owner = lookup(owners, d.getOwnerId());
            AgencyRef agency = lookup(linkedAgencies, d.getAgencyId());
            TenderRef tender = lookup(linkedTenders, d.getTenderId());
            OpenTasks t = open.get(d.getId());
            result.add(new PipelineView.Deal(
                    d.getId(),
                    d.getTitle(),
                    d.getStageId(),
                    stages.get(d.getStageId()).getName(),
                    d.getPosition(),
                    d.getStatus(),
                    d.isNew(now),
                    d.getValue(),
                    d.getPriority(),
                    owner == null ? null : new PipelineView.Owner(owner.id(), owner.name(), owner.initials()),
                    agency == null
                            ? null
                            : new PipelineView.AgencyLink(agency.id(), agency.name(), agency.city(), agency.state()),
                    tender == null
                            ? null
                            : new PipelineView.TenderLink(
                                    tender.id(),
                                    tender.agencyName(),
                                    tender.identification(),
                                    tender.totalValue(),
                                    tender.status()),
                    d.getNotes(),
                    t == null ? 0 : t.open(),
                    t == null || t.overdueTitle() == null
                            ? null
                            : new PipelineView.OverdueTask(t.overdueTitle(), t.overdueDueAt(), t.overdue()),
                    d.getClosedAt(),
                    d.getVersion() == null ? 0 : d.getVersion(),
                    me.canEditOwnedBy(d.getOwnerId())));
        }
        return result;
    }

    private static Set<UUID> ids(List<Deal> list, Function<Deal, UUID> id) {
        return list.stream().map(id).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /** Mapa vazio pode ser {@code Map.of()}, que não aceita {@code get(null)}; negociação sem vínculo é normal. */
    private static <T> T lookup(Map<UUID, T> map, UUID id) {
        return id == null ? null : map.get(id);
    }
}
