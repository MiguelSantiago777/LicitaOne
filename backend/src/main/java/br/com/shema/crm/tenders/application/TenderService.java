package br.com.shema.crm.tenders.application;

import br.com.shema.crm.identity.UserDirectory;
import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.domain.Money;
import br.com.shema.crm.shared.security.CurrentUser;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import br.com.shema.crm.tenders.TenderLifecycle;
import br.com.shema.crm.tenders.TenderRef;
import br.com.shema.crm.tenders.domain.Agency;
import br.com.shema.crm.tenders.domain.Modality;
import br.com.shema.crm.tenders.domain.ProcessNumber;
import br.com.shema.crm.tenders.domain.Tender;
import br.com.shema.crm.tenders.domain.TenderDraft;
import br.com.shema.crm.tenders.domain.TenderEvent;
import br.com.shema.crm.tenders.domain.TenderStatus;
import br.com.shema.crm.tenders.domain.Uasg;
import br.com.shema.crm.tenders.infrastructure.AgencyRepository;
import br.com.shema.crm.tenders.infrastructure.TenderEventRepository;
import br.com.shema.crm.tenders.infrastructure.TenderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional
public class TenderService implements TenderLifecycle {

    /** Dados de entrada já validados pelo controller (formato), antes das regras do domínio. */
    public record TenderInput(
            UUID agencyId,
            Modality modality,
            String uasg,
            String processNumber,
            String portal,
            OffsetDateTime sessionAt,
            String object,
            String materialType,
            Integer itemsQuantity,
            BigDecimal totalValue,
            UUID ownerId,
            String notes) {}

    public record StatusTotal(TenderStatus status, long count, BigDecimal value) {}

    public record FieldChange(String field, String from, String to) {}

    private final TenderRepository tenders;
    private final AgencyRepository agencies;
    private final TenderEventRepository events;
    private final UserDirectory userDirectory;
    private final EntityManager entityManager;
    private final JsonMapper json;
    private final Clock clock;

    public TenderService(
            TenderRepository tenders,
            AgencyRepository agencies,
            TenderEventRepository events,
            UserDirectory userDirectory,
            EntityManager entityManager,
            JsonMapper json,
            Clock clock) {
        this.tenders = tenders;
        this.agencies = agencies;
        this.events = events;
        this.userDirectory = userDirectory;
        this.entityManager = entityManager;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<Tender> search(TenderFilter filter, Pageable pageable) {
        return tenders.findAll(TenderSpecifications.matching(filter), pageable);
    }

    /** Quantidade e soma por estado, respeitando o mesmo filtro da lista (sem o filtro de estado). */
    @Transactional(readOnly = true)
    public List<StatusTotal> summary(TenderFilter filter) {
        TenderFilter withoutStatus = new TenderFilter(
                null,
                filter.q(),
                filter.agencyId(),
                filter.city(),
                filter.state(),
                filter.materialType(),
                filter.minValue(),
                filter.maxValue(),
                filter.from(),
                filter.to(),
                filter.ownerId(),
                filter.signedBefore());
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Tender> root = query.from(Tender.class);
        List<Predicate> predicates = TenderSpecifications.predicates(withoutStatus, root, cb);
        query.multiselect(
                        root.get("status"),
                        cb.count(root),
                        cb.coalesce(cb.sum(root.<BigDecimal>get("totalValue")), BigDecimal.ZERO))
                .where(predicates.toArray(Predicate[]::new))
                .groupBy(root.get("status"));
        Map<TenderStatus, StatusTotal> byStatus = new EnumMap<>(TenderStatus.class);
        for (TenderStatus status : TenderStatus.values()) {
            byStatus.put(status, new StatusTotal(status, 0, BigDecimal.ZERO.setScale(2)));
        }
        for (Tuple row : entityManager.createQuery(query).getResultList()) {
            TenderStatus status = row.get(0, TenderStatus.class);
            byStatus.put(
                    status,
                    new StatusTotal(
                            status,
                            row.get(1, Long.class),
                            row.get(2, BigDecimal.class).setScale(2)));
        }
        return List.copyOf(byStatus.values());
    }

    @Transactional(readOnly = true)
    public Tender get(UUID id) {
        return tenders.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Licitação não encontrada."));
    }

    @Transactional(readOnly = true)
    public List<TenderEvent> history(UUID id) {
        get(id);
        return events.findByTenderIdOrderByOccurredAtDesc(id);
    }

    @Transactional(readOnly = true)
    public Map<String, List<String>> suggestions() {
        Map<String, List<String>> s = new LinkedHashMap<>();
        s.put("materialTypes", tenders.distinctMaterialTypes());
        s.put("portals", tenders.distinctPortals());
        return s;
    }

    public Tender register(TenderInput input, TenderStatus initialStatus, LocalDate wonAt) {
        CurrentUser user = CurrentUser.get();
        UUID ownerId = input.ownerId() == null ? user.id() : input.ownerId();
        user.requireCanEditOwnedBy(ownerId);
        Tender tender = Tender.register(draft(input, ownerId), initialStatus, wonAt, today());
        tenders.save(tender);
        record(tender, TenderEvent.Type.CREATED, null, tender.getStatus(), tender.getWonAt(), null, user);
        return tender;
    }

    public Tender update(UUID id, long expectedVersion, TenderInput input) {
        CurrentUser user = CurrentUser.get();
        Tender tender = get(id);
        user.requireCanEditOwnedBy(tender.getOwnerId());
        checkVersion(tender, expectedVersion);
        UUID ownerId = input.ownerId() == null ? tender.getOwnerId() : input.ownerId();
        if (!Objects.equals(ownerId, tender.getOwnerId()) && !user.isAdmin()) {
            user.requireCanEditOwnedBy(ownerId);
        }
        Map<String, String> before = tender.snapshot();
        tender.update(draft(input, ownerId));
        List<FieldChange> changes = diff(before, tender.snapshot());
        if (!changes.isEmpty()) {
            record(tender, TenderEvent.Type.UPDATED, null, null, null, changes, user);
        }
        tenders.flush();
        return tender;
    }

    public Tender transition(UUID id, long expectedVersion, TenderStatus target, LocalDate date) {
        CurrentUser user = CurrentUser.get();
        Tender tender = get(id);
        user.requireCanEditOwnedBy(tender.getOwnerId());
        checkVersion(tender, expectedVersion);
        if (target != null && target.isManagedByContracts()) {
            throw new DomainRuleViolation(
                    "MANAGED_BY_CONTRACTS",
                    "\"" + target.label() + "\" é registrado pelos contratos: assine, registre um pedido ou encerre"
                            + " o contrato na aba Itens e pedidos.");
        }
        TenderStatus from = tender.getStatus();
        tender.transitionTo(target, date, today());
        record(tender, TenderEvent.Type.STATUS_CHANGED, from, target, date, null, user);
        tenders.flush();
        return tender;
    }

    // ---- API pública para o módulo de contratos (TenderLifecycle) ----

    @Override
    @Transactional(readOnly = true)
    public Optional<TenderRef> find(UUID tenderId) {
        return tenderId == null ? Optional.empty() : tenders.findById(tenderId).map(TenderService::ref);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, TenderRef> findAll(Collection<UUID> tenderIds) {
        if (tenderIds.isEmpty()) {
            return Map.of();
        }
        return tenders.findAllWithAgencyByIdIn(tenderIds).stream()
                .map(TenderService::ref)
                .collect(Collectors.toMap(TenderRef::id, Function.identity()));
    }

    @Override
    public void contractSigned(UUID tenderId, LocalDate signedAt, CurrentUser by) {
        Tender tender = get(tenderId);
        TenderStatus from = tender.getStatus();
        if (tender.onContractSigned(signedAt, today())) {
            record(tender, TenderEvent.Type.STATUS_CHANGED, from, tender.getStatus(), signedAt, null, by);
        }
    }

    @Override
    public void orderRegistered(UUID tenderId, LocalDate orderedAt, CurrentUser by) {
        Tender tender = get(tenderId);
        TenderStatus from = tender.getStatus();
        if (tender.onOrderRegistered(orderedAt, today())) {
            record(tender, TenderEvent.Type.STATUS_CHANGED, from, tender.getStatus(), orderedAt, null, by);
        }
    }

    @Override
    public void allContractsClosed(UUID tenderId, LocalDate closedAt, CurrentUser by) {
        Tender tender = get(tenderId);
        TenderStatus from = tender.getStatus();
        if (tender.onAllContractsClosed(closedAt, today())) {
            record(tender, TenderEvent.Type.STATUS_CHANGED, from, tender.getStatus(), closedAt, null, by);
        }
    }

    private static TenderRef ref(Tender t) {
        return new TenderRef(
                t.getId(),
                t.getAgency().getId(),
                t.getAgency().getName(),
                t.reference(),
                t.getObject(),
                t.getMaterialType(),
                t.getTotalValue().amount(),
                t.getStatus().name(),
                t.getWonAt(),
                t.getOwnerId(),
                t.getStatus().acceptsContracts());
    }

    private TenderDraft draft(TenderInput input, UUID ownerId) {
        Agency agency = agencies.findById(input.agencyId())
                .orElseThrow(() -> new DomainRuleViolation("AGENCY_NOT_FOUND", "Órgão não encontrado."));
        if (ownerId != null && userDirectory.find(ownerId).isEmpty()) {
            throw new DomainRuleViolation("OWNER_NOT_FOUND", "Responsável não encontrado.");
        }
        if (input.totalValue() == null) {
            throw new DomainRuleViolation("REQUIRED", "Informe o valor.");
        }
        return new TenderDraft(
                agency,
                input.modality(),
                Uasg.ofNullable(input.uasg()),
                ProcessNumber.ofNullable(input.processNumber()),
                input.portal(),
                input.sessionAt(),
                input.object(),
                input.materialType(),
                input.itemsQuantity(),
                Money.of(input.totalValue()),
                ownerId,
                input.notes());
    }

    private void checkVersion(Tender tender, long expectedVersion) {
        if (tender.getVersion() == null || tender.getVersion() != expectedVersion) {
            throw new ApiException(
                    ErrorCode.VERSION_CONFLICT,
                    "Outra pessoa alterou esta licitação enquanto você editava. Recarregue para ver a versão atual.");
        }
    }

    private List<FieldChange> diff(Map<String, String> before, Map<String, String> after) {
        List<FieldChange> changes = new ArrayList<>();
        for (Map.Entry<String, String> entry : after.entrySet()) {
            String old = before.get(entry.getKey());
            if (!Objects.equals(old, entry.getValue())) {
                changes.add(new FieldChange(
                        entry.getKey(), display(entry.getKey(), old), display(entry.getKey(), entry.getValue())));
            }
        }
        return changes;
    }

    /** O responsável é guardado por id; no histórico aparece o nome. */
    private String display(String field, String value) {
        if (value != null && field.equals("Responsável")) {
            return userDirectory.find(UUID.fromString(value)).map(u -> u.name()).orElse(value);
        }
        return value;
    }

    private void record(
            Tender tender,
            TenderEvent.Type type,
            TenderStatus from,
            TenderStatus to,
            LocalDate date,
            List<FieldChange> changes,
            CurrentUser user) {
        events.save(new TenderEvent(
                tender.getId(),
                type,
                from,
                to,
                date,
                changes == null ? null : json.writeValueAsString(changes),
                user.id(),
                user.name(),
                clock.instant()));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
