package br.com.shema.crm.contracts.application;

import br.com.shema.crm.contracts.ContractEvents.OrderRegistered;
import br.com.shema.crm.contracts.ContractItemsImport;
import br.com.shema.crm.contracts.domain.BrFormat;
import br.com.shema.crm.contracts.domain.Contract;
import br.com.shema.crm.contracts.domain.ContractEvent;
import br.com.shema.crm.contracts.domain.ContractOrder;
import br.com.shema.crm.contracts.domain.ItemImportPlan;
import br.com.shema.crm.contracts.domain.OrderDraft;
import br.com.shema.crm.contracts.infrastructure.ContractEventRepository;
import br.com.shema.crm.contracts.infrastructure.ContractRepository;
import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.messaging.EventMetadata;
import br.com.shema.crm.shared.security.CurrentUser;
import br.com.shema.crm.tenders.TenderLifecycle;
import br.com.shema.crm.tenders.TenderRef;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class ContractImportService implements ContractItemsImport {

    static final String ORDER_REFERENCE = "Importação de planilha";

    private final ContractRepository contracts;
    private final ContractEventRepository events;
    private final TenderLifecycle tenders;
    private final ApplicationEventPublisher publisher;
    private final EntityManager entityManager;
    private final Clock clock;

    ContractImportService(
            ContractRepository contracts,
            ContractEventRepository events,
            TenderLifecycle tenders,
            ApplicationEventPublisher publisher,
            EntityManager entityManager,
            Clock clock) {
        this.contracts = contracts;
        this.events = events;
        this.tenders = tenders;
        this.publisher = publisher;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ContractSummary> find(UUID contractId) {
        return contracts
                .findById(contractId)
                .flatMap(c -> tenders.find(c.getTenderId())
                        .map(t -> new ContractSummary(c.getId(), t.id(), t.ownerId(), label(c, t), c.isClosed())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RowOutcome> preview(UUID contractId, List<ImportedItem> rows) {
        Contract contract = load(contractId);
        return outcomes(ItemImportPlan.of(contract, inputs(rows)));
    }

    @Override
    public ImportOutcome apply(UUID contractId, List<ImportedItem> rows, LocalDate orderDate, CurrentUser by) {
        Contract contract = load(contractId);
        TenderRef tender = tenders.find(contract.getTenderId()).orElseThrow();
        by.requireCanEditOwnedBy(tender.ownerId());
        if (contract.isClosed()) {
            throw new DomainRuleViolation("CONTRACT_CLOSED", "Este contrato já foi encerrado.");
        }
        entityManager.lock(contract, LockModeType.PESSIMISTIC_FORCE_INCREMENT);

        ItemImportPlan plan = ItemImportPlan.of(contract, inputs(rows));
        if (plan.changesItems()) {
            contract.replaceItems(plan.items());
            record(contract, ContractEvent.Type.ITEMS_CHANGED, null, summaryOfItems(plan), by);
        }
        OrderDraft.Line[] lines = plan.orderLines(contract);
        UUID orderId = null;
        BigDecimal orderValue = BigDecimal.ZERO.setScale(2);
        if (lines.length > 0) {
            ContractOrder order = contract.placeOrder(
                    new OrderDraft(orderDate, ORDER_REFERENCE, null, null, List.of(lines)),
                    LocalDate.now(clock),
                    clock.instant(),
                    by.name());
            orderId = order.getId();
            orderValue = order.getValue();
            tenders.orderRegistered(contract.getTenderId(), order.getOrderedAt(), by);
            record(
                    contract,
                    ContractEvent.Type.ORDER_PLACED,
                    order.getOrderedAt(),
                    "Pedido de " + BrFormat.money(orderValue) + " · " + ORDER_REFERENCE,
                    by);
            publisher.publishEvent(new OrderRegistered(
                    EventMetadata.create(clock),
                    contract.getId(),
                    contract.getTenderId(),
                    order.getId(),
                    order.getOrderedAt(),
                    order.getValue(),
                    contract.balance().remaining()));
        }
        contracts.flush();
        return new ImportOutcome(outcomes(plan), orderId, orderValue);
    }

    private Contract load(UUID id) {
        return contracts
                .findById(id)
                .orElseThrow(() -> new DomainRuleViolation("CONTRACT_NOT_FOUND", "Contrato não encontrado."));
    }

    private static List<ItemImportPlan.Input> inputs(List<ImportedItem> rows) {
        return rows.stream()
                .map(r -> new ItemImportPlan.Input(
                        r.rowNumber(),
                        r.lot(),
                        r.description(),
                        r.unit(),
                        r.quantity(),
                        r.unitPrice(),
                        r.orderedQuantity()))
                .toList();
    }

    private static List<RowOutcome> outcomes(ItemImportPlan plan) {
        return plan.rows().stream()
                .map(r -> new RowOutcome(
                        r.rowNumber(),
                        switch (r.action()) {
                            case CREATE -> RowStatus.CREATED;
                            case UPDATE -> RowStatus.UPDATED;
                            case KEEP -> RowStatus.UNCHANGED;
                            case WARN -> RowStatus.WARNING;
                        },
                        r.message(),
                        r.newOrderQuantity()))
                .toList();
    }

    private static String summaryOfItems(ItemImportPlan plan) {
        long created = plan.rows().stream()
                .filter(r -> r.action() == ItemImportPlan.Action.CREATE)
                .count();
        long updated = plan.rows().stream()
                .filter(r -> r.action() == ItemImportPlan.Action.UPDATE)
                .count();
        return "Planilha importada: " + created + (created == 1 ? " item novo" : " itens novos") + ", " + updated
                + (updated == 1 ? " alterado" : " alterados");
    }

    private void record(Contract contract, ContractEvent.Type type, LocalDate date, String summary, CurrentUser by) {
        events.save(new ContractEvent(
                contract.getId(),
                contract.getTenderId(),
                type,
                date,
                summary,
                null,
                by.id(),
                by.name(),
                clock.instant()));
    }

    private static String label(Contract c, TenderRef t) {
        return t.agencyName() + " · " + c.getKind().label() + (c.getNumber() == null ? "" : " " + c.getNumber());
    }
}
