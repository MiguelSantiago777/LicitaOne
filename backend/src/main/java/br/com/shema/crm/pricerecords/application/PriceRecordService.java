package br.com.shema.crm.pricerecords.application;

import br.com.shema.crm.deals.DealDirectory;
import br.com.shema.crm.deals.DealRef;
import br.com.shema.crm.pricerecords.application.PriceRecordViews.CaronaCheck;
import br.com.shema.crm.pricerecords.application.PriceRecordViews.CaronaView;
import br.com.shema.crm.pricerecords.application.PriceRecordViews.Detail;
import br.com.shema.crm.pricerecords.application.PriceRecordViews.Event;
import br.com.shema.crm.pricerecords.application.PriceRecordViews.Highlight;
import br.com.shema.crm.pricerecords.application.PriceRecordViews.Prospect;
import br.com.shema.crm.pricerecords.application.PriceRecordViews.Situation;
import br.com.shema.crm.pricerecords.application.PriceRecordViews.Summary;
import br.com.shema.crm.pricerecords.application.PriceRecordViews.TenderLink;
import br.com.shema.crm.pricerecords.application.PriceRecordViews.Warning;
import br.com.shema.crm.pricerecords.domain.Carona;
import br.com.shema.crm.pricerecords.domain.CaronaLimits;
import br.com.shema.crm.pricerecords.domain.Document;
import br.com.shema.crm.pricerecords.domain.PriceRecord;
import br.com.shema.crm.pricerecords.domain.PriceRecordDrafts.CaronaDraft;
import br.com.shema.crm.pricerecords.domain.PriceRecordDrafts.ItemDraft;
import br.com.shema.crm.pricerecords.domain.PriceRecordDrafts.RecordDraft;
import br.com.shema.crm.pricerecords.domain.PriceRecordEvent;
import br.com.shema.crm.pricerecords.domain.PriceRecordItem;
import br.com.shema.crm.pricerecords.infrastructure.DocumentRepository;
import br.com.shema.crm.pricerecords.infrastructure.PriceRecordEventRepository;
import br.com.shema.crm.pricerecords.infrastructure.PriceRecordRepository;
import br.com.shema.crm.shared.domain.BrFormat;
import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.security.CurrentUser;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import br.com.shema.crm.tenders.AgencyDirectory;
import br.com.shema.crm.tenders.AgencyRef;
import br.com.shema.crm.tenders.TenderLifecycle;
import br.com.shema.crm.tenders.TenderRef;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
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
 * Atas e caronas. Alterar segue a regra da licitação (responsável ou administrador). Itens e caronas mudam o agregado
 * com {@code PESSIMISTIC_FORCE_INCREMENT}: duas caronas simultâneas não passam pela mesma checagem de limite, e a
 * resposta já sai com a versão nova.
 */
@Service
@Transactional
public class PriceRecordService {

    /** Estados em que a licitação já foi vencida: só elas têm ata. */
    private static final Set<String> WON =
            Set.of("VENCIDA", "AGUARDANDO_ASSINATURA", "CONTRATO_ASSINADO", "EM_ANDAMENTO", "ENCERRADO");

    public enum Filter {
        VIGENTES,
        OPORTUNIDADES,
        VENCENDO,
        VENCIDAS,
        TODAS
    }

    private final PriceRecordRepository records;
    private final DocumentRepository documents;
    private final PriceRecordEventRepository events;
    private final TenderLifecycle tenders;
    private final AgencyDirectory agencies;
    private final DealDirectory deals;
    private final CaronaProperties properties;
    private final EntityManager entityManager;
    private final Clock clock;

    public PriceRecordService(
            PriceRecordRepository records,
            DocumentRepository documents,
            PriceRecordEventRepository events,
            TenderLifecycle tenders,
            AgencyDirectory agencies,
            DealDirectory deals,
            CaronaProperties properties,
            EntityManager entityManager,
            Clock clock) {
        this.records = records;
        this.documents = documents;
        this.events = events;
        this.tenders = tenders;
        this.agencies = agencies;
        this.deals = deals;
        this.properties = properties;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /** Lista ordenada pelo vencimento. São dezenas de atas, não milhares: filtro e busca rodam em memória. */
    @Transactional(readOnly = true)
    public List<Summary> list(Filter filter, String q) {
        List<PriceRecord> all = records.findAllByOrderByValidUntilAsc();
        Map<UUID, TenderRef> linked =
                tenders.findAll(all.stream().map(PriceRecord::getTenderId).collect(Collectors.toSet()));
        LocalDate today = today();
        String query = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(r -> matches(r, filter == null ? Filter.VIGENTES : filter, today))
                .map(r -> summary(r, linked.get(r.getTenderId()), today))
                .filter(s -> query == null || matches(s, query))
                .toList();
    }

    @Transactional(readOnly = true)
    public Detail byTender(UUID tenderId) {
        PriceRecord record = records.findByTenderId(tenderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Esta licitação ainda não tem ata."));
        return detail(record);
    }

    @Transactional(readOnly = true)
    public Detail get(UUID id) {
        return detail(load(id));
    }

    /** Para as listas de licitações destacarem quem tem ata vigente, numa consulta só. */
    @Transactional(readOnly = true)
    public List<Highlight> highlights(Collection<UUID> tenderIds) {
        if (tenderIds.isEmpty()) {
            return List.of();
        }
        LocalDate today = today();
        CaronaLimits limits = properties.limits();
        return records.findByTenderIdIn(tenderIds).stream()
                .map(r -> new Highlight(
                        r.getTenderId(),
                        r.getId(),
                        r.getValidUntil(),
                        situation(r, today),
                        r.isOpportunity(today, limits)))
                .toList();
    }

    public Detail create(UUID tenderId, RecordDraft draft) {
        TenderRef tender = tender(tenderId);
        CurrentUser user = CurrentUser.get();
        user.requireCanEditOwnedBy(tender.ownerId());
        if (!WON.contains(tender.status())) {
            throw new DomainRuleViolation("TENDER_NOT_WON", "A ata é registrada depois que a licitação é vencida.");
        }
        if (records.findByTenderId(tenderId).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "Esta licitação já tem ata.");
        }
        validateDocument(tenderId, draft.documentId());
        PriceRecord record = records.saveAndFlush(new PriceRecord(tenderId, draft));
        record(
                record,
                PriceRecordEvent.Type.ATA_CREATED,
                "Ata " + record.getNumber() + " registrada, válida até " + date(record.getValidUntil())
                        + (record.isAllowsAdhesion() ? "" : " (não permite adesão)"),
                user);
        return detail(record);
    }

    public Detail update(UUID id, long expectedVersion, RecordDraft draft) {
        CurrentUser user = CurrentUser.get();
        PriceRecord record = editable(id, expectedVersion, user);
        validateDocument(record.getTenderId(), draft.documentId());
        LocalDate before = record.getValidUntil();
        record.update(draft);
        records.flush();
        record(
                record,
                PriceRecordEvent.Type.ATA_UPDATED,
                "Ata " + record.getNumber() + " alterada"
                        + (before.equals(record.getValidUntil())
                                ? ""
                                : "; vigência de " + date(before) + " para " + date(record.getValidUntil())),
                user);
        return detail(record);
    }

    public Detail replaceItems(UUID id, long expectedVersion, List<ItemDraft> items) {
        CurrentUser user = CurrentUser.get();
        PriceRecord record = changing(id, expectedVersion, user);
        record.replaceItems(items);
        records.flush();
        record(
                record,
                PriceRecordEvent.Type.ITEMS_CHANGED,
                "Itens da ata: " + items.size()
                        + (items.size() == 1 ? " item" : " itens") + ", "
                        + BrFormat.money(record.itemsValue() == null ? BigDecimal.ZERO : record.itemsValue()),
                user);
        return detail(record);
    }

    @Transactional(readOnly = true)
    public CaronaCheck checkCarona(UUID id, CaronaDraft draft) {
        PriceRecord record = load(id);
        TenderRef tender = tender(record.getTenderId());
        validateCarona(tender, draft);
        PriceRecord.CaronaCheck check = record.checkCarona(draft, tender.totalValue(), properties.limits());
        return new CaronaCheck(
                check.value(),
                check.warnings().stream()
                        .map(w -> new Warning(w.code(), w.message()))
                        .toList());
    }

    /** Sem versão no pedido: carona é acréscimo. A trava do agregado impede duas passarem do limite juntas. */
    public Detail registerCarona(UUID id, CaronaDraft draft, boolean confirmLimits) {
        CurrentUser user = CurrentUser.get();
        PriceRecord record = load(id);
        TenderRef tender = tender(record.getTenderId());
        user.requireCanEditOwnedBy(tender.ownerId());
        entityManager.lock(record, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        AgencyRef agency = validateCarona(tender, draft);
        Carona carona = record.registerCarona(
                draft, tender.totalValue(), properties.limits(), confirmLimits, user.name(), clock.instant());
        records.flush();
        record(
                record,
                PriceRecordEvent.Type.CARONA_REGISTERED,
                "Carona de " + agency.name() + " em "
                        + date(carona.getAdheredAt()) + ", " + BrFormat.money(carona.getValue())
                        + (carona.isLimitsOverridden() ? " (passou dos limites, confirmada)" : ""),
                user);
        return detail(record);
    }

    public Detail removeCarona(UUID id, UUID caronaId, long expectedVersion) {
        CurrentUser user = CurrentUser.get();
        PriceRecord record = changing(id, expectedVersion, user);
        Carona carona = record.removeCarona(caronaId);
        records.flush();
        String agency = agencies.find(carona.getAgencyId()).map(AgencyRef::name).orElse("órgão");
        record(
                record,
                PriceRecordEvent.Type.CARONA_REMOVED,
                "Carona de " + agency + " de " + date(carona.getAdheredAt()) + " apagada ("
                        + BrFormat.money(carona.getValue()) + ")",
                user);
        return detail(record);
    }

    /** Ata com carona não sai: a carona depende dela. */
    public void delete(UUID id, long expectedVersion) {
        CurrentUser user = CurrentUser.get();
        PriceRecord record = editable(id, expectedVersion, user);
        if (!record.getCaronas().isEmpty()) {
            throw new DomainRuleViolation("ATA_HAS_CARONAS", "A ata tem carona registrada. Apague as caronas antes.");
        }
        records.delete(record);
        record(record, PriceRecordEvent.Type.ATA_DELETED, "Ata " + record.getNumber() + " apagada", user);
    }

    /** Prefeituras sem licitação vencida e sem carona: onde oferecer a ata. */
    @Transactional(readOnly = true)
    public List<Prospect> prospects(String state, String q) {
        Set<UUID> withCarona = new HashSet<>(records.agenciesWithCarona());
        String query = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        return agencies.municipalitiesWithoutWonTender(state).stream()
                .filter(a -> !withCarona.contains(a.id()))
                .filter(a -> query == null
                        || a.name().toLowerCase(Locale.ROOT).contains(query)
                        || a.city().toLowerCase(Locale.ROOT).contains(query))
                .map(a -> new Prospect(a.id(), a.name(), a.city(), a.state()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Event> history(UUID tenderId) {
        return events.findByTenderIdOrderByOccurredAtDesc(tenderId).stream()
                .map(e -> new Event(e.getId(), e.getType().name(), e.getSummary(), e.getUserName(), e.getOccurredAt()))
                .toList();
    }

    private AgencyRef validateCarona(TenderRef tender, CaronaDraft draft) {
        if (draft.agencyId() == null) {
            throw new DomainRuleViolation("REQUIRED", "Escolha o órgão que aderiu.");
        }
        AgencyRef agency = agencies.find(draft.agencyId())
                .orElseThrow(() -> new DomainRuleViolation("AGENCY_NOT_FOUND", "Órgão não encontrado."));
        if (agency.id().equals(tender.agencyId())) {
            throw new DomainRuleViolation(
                    "OWN_AGENCY", "O órgão da licitação é o dono da ata; carona é de outro órgão.");
        }
        if (draft.dealId() != null && deals.find(draft.dealId()).isEmpty()) {
            throw new DomainRuleViolation("DEAL_NOT_FOUND", "Negociação não encontrada.");
        }
        return agency;
    }

    private void validateDocument(UUID tenderId, UUID documentId) {
        if (documentId == null) {
            return;
        }
        documents
                .findById(documentId)
                .filter(d -> !d.isDeleted() && d.getTenderId().equals(tenderId))
                .orElseThrow(() ->
                        new DomainRuleViolation("DOCUMENT_NOT_FOUND", "Documento não encontrado nesta licitação."));
    }

    private PriceRecord load(UUID id) {
        return records.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Ata não encontrada."));
    }

    private PriceRecord editable(UUID id, long expectedVersion, CurrentUser user) {
        PriceRecord record = load(id);
        user.requireCanEditOwnedBy(tender(record.getTenderId()).ownerId());
        if (record.getVersion() == null || record.getVersion() != expectedVersion) {
            throw new ApiException(
                    ErrorCode.VERSION_CONFLICT,
                    "Outra pessoa alterou esta ata enquanto você editava. Recarregue para ver a versão atual.");
        }
        return record;
    }

    /** Itens e caronas ficam em tabelas próprias: a versão da ata sobe na hora para a resposta já sair com ela. */
    private PriceRecord changing(UUID id, long expectedVersion, CurrentUser user) {
        PriceRecord record = editable(id, expectedVersion, user);
        entityManager.lock(record, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        return record;
    }

    private TenderRef tender(UUID tenderId) {
        return tenders.find(tenderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Licitação não encontrada."));
    }

    private void record(PriceRecord record, PriceRecordEvent.Type type, String summary, CurrentUser user) {
        events.save(new PriceRecordEvent(record.getTenderId(), type, summary, user.id(), user.name(), clock.instant()));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private boolean matches(PriceRecord r, Filter filter, LocalDate today) {
        return switch (filter) {
            case VIGENTES -> r.isValidOn(today);
            case OPORTUNIDADES -> r.isOpportunity(today, properties.limits());
            case VENCENDO -> situation(r, today) == Situation.VENCENDO;
            case VENCIDAS -> !r.isValidOn(today);
            case TODAS -> true;
        };
    }

    private static boolean matches(Summary s, String q) {
        TenderLink t = s.tender();
        return s.number().toLowerCase(Locale.ROOT).contains(q)
                || (t != null
                        && (contains(t.agencyName(), q)
                                || contains(t.identification(), q)
                                || contains(t.object(), q)
                                || contains(t.materialType(), q)));
    }

    private static boolean contains(String text, String q) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(q);
    }

    private Situation situation(PriceRecord r, LocalDate today) {
        if (!r.isValidOn(today)) {
            return Situation.VENCIDA;
        }
        return ChronoUnit.DAYS.between(today, r.getValidUntil()) <= properties.expiringDays()
                ? Situation.VENCENDO
                : Situation.VIGENTE;
    }

    private static BigDecimal reference(PriceRecord r, TenderRef tender) {
        BigDecimal items = r.itemsValue();
        if (items != null) {
            return items;
        }
        return tender == null || tender.totalValue() == null ? BigDecimal.ZERO.setScale(2) : tender.totalValue();
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0
                ? BigDecimal.ZERO.setScale(1)
                : part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP);
    }

    private static TenderLink tenderLink(TenderRef t) {
        return t == null
                ? null
                : new TenderLink(
                        t.id(),
                        t.agencyName(),
                        t.identification(),
                        t.object(),
                        t.materialType(),
                        t.status(),
                        t.totalValue());
    }

    private Summary summary(PriceRecord r, TenderRef tender, LocalDate today) {
        BigDecimal reference = reference(r, tender);
        return new Summary(
                r.getId(),
                tenderLink(tender),
                r.getNumber(),
                r.getValidUntil(),
                ChronoUnit.DAYS.between(today, r.getValidUntil()),
                situation(r, today),
                r.isAllowsAdhesion(),
                r.isOpportunity(today, properties.limits()),
                r.agencyCount(),
                reference,
                r.caronasValue(),
                percent(r.caronasValue(), reference));
    }

    private Detail detail(PriceRecord r) {
        TenderRef tender = tender(r.getTenderId());
        LocalDate today = today();
        CaronaLimits limits = properties.limits();
        BigDecimal reference = reference(r, tender);
        Map<UUID, PriceRecord.ItemUsage> usage =
                r.usage(limits).stream().collect(Collectors.toMap(PriceRecord.ItemUsage::itemId, Function.identity()));
        Map<UUID, PriceRecordItem> itemsById =
                r.getItems().stream().collect(Collectors.toMap(PriceRecordItem::getId, Function.identity()));
        Map<UUID, AgencyRef> caronaAgencies = agencies.findAll(
                r.getCaronas().stream().map(Carona::getAgencyId).collect(Collectors.toSet()));
        Map<UUID, DealRef> caronaDeals = deals.findAll(r.getCaronas().stream()
                .map(Carona::getDealId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Document document = r.getDocumentId() == null
                ? null
                : documents
                        .findById(r.getDocumentId())
                        .filter(d -> !d.isDeleted())
                        .orElse(null);
        return new Detail(
                r.getId(),
                tenderLink(tender),
                r.getNumber(),
                r.getSignedAt(),
                r.getValidUntil(),
                ChronoUnit.DAYS.between(today, r.getValidUntil()),
                situation(r, today),
                r.isAllowsAdhesion(),
                r.isOpportunity(today, limits),
                r.getNotes(),
                document == null
                        ? null
                        : new PriceRecordViews.DocumentLink(
                                document.getId(), document.getTitle(), document.getCurrentVersion()),
                new PriceRecordViews.Limits(limits.maxAgencies(), limits.maxTotalPercent()),
                r.agencyCount(),
                reference,
                r.caronasValue(),
                percent(r.caronasValue(), reference),
                r.getItems().stream()
                        .map(i -> new PriceRecordViews.Item(
                                i.getId(),
                                i.getLot(),
                                i.getDescription(),
                                i.getUnit(),
                                i.getQuantity(),
                                i.getUnitPrice(),
                                i.total(),
                                usage.get(i.getId()).used(),
                                usage.get(i.getId()).limit()))
                        .toList(),
                r.getCaronas().stream()
                        .map(c -> caronaView(
                                c, caronaAgencies.get(c.getAgencyId()), lookup(caronaDeals, c.getDealId()), itemsById))
                        .toList(),
                r.getVersion() == null ? 0 : r.getVersion(),
                CurrentUser.get().canEditOwnedBy(tender.ownerId()));
    }

    private static CaronaView caronaView(
            Carona c, AgencyRef agency, DealRef deal, Map<UUID, PriceRecordItem> itemsById) {
        return new CaronaView(
                c.getId(),
                agency == null
                        ? null
                        : new PriceRecordViews.AgencyLink(agency.id(), agency.name(), agency.city(), agency.state()),
                c.getAdheredAt(),
                c.getValue(),
                deal == null ? null : new PriceRecordViews.DealLink(deal.id(), deal.title(), deal.status()),
                c.getLines().stream()
                        .map(l -> new PriceRecordViews.CaronaLine(
                                l.itemId(),
                                itemsById.containsKey(l.itemId())
                                        ? itemsById.get(l.itemId()).getDescription()
                                        : null,
                                l.quantity()))
                        .toList(),
                c.getNotes(),
                c.isLimitsOverridden(),
                c.getOverrideNote(),
                c.getCreatedByName(),
                c.getCreatedAt());
    }

    /** Mapa vazio pode ser {@code Map.of()}, que não aceita {@code get(null)}; carona sem negociação é normal. */
    private static <T> T lookup(Map<UUID, T> map, UUID id) {
        return id == null ? null : map.get(id);
    }

    private static String date(LocalDate d) {
        return String.format("%02d/%02d/%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }
}
