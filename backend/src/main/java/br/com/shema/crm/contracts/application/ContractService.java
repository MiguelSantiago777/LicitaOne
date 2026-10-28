package br.com.shema.crm.contracts.application;

import br.com.shema.crm.contracts.ContractEvents.ContractClosed;
import br.com.shema.crm.contracts.ContractEvents.ContractSigned;
import br.com.shema.crm.contracts.ContractEvents.OrderRegistered;
import br.com.shema.crm.contracts.domain.Contract;
import br.com.shema.crm.contracts.domain.ContractDraft;
import br.com.shema.crm.contracts.domain.ContractEvent;
import br.com.shema.crm.contracts.domain.ContractOrder;
import br.com.shema.crm.contracts.domain.ItemDraft;
import br.com.shema.crm.contracts.domain.OrderDraft;
import br.com.shema.crm.contracts.infrastructure.ContractEventRepository;
import br.com.shema.crm.contracts.infrastructure.ContractRepository;
import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.persistence.IdempotencyKeys;
import br.com.shema.crm.shared.security.CurrentUser;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import br.com.shema.crm.tenders.TenderLifecycle;
import br.com.shema.crm.tenders.TenderRef;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Casos de uso de contratos. Cada mudança relevante avisa a licitação ({@link TenderLifecycle}) na mesma transação,
 * grava o histórico e publica um evento de domínio.
 */
@Service
@Transactional
public class ContractService {

    static final String ORDER_SCOPE = "contract-order";

    public record FieldChange(String field, String from, String to) {}

    private final ContractRepository contracts;
    private final ContractEventRepository events;
    private final TenderLifecycle tenders;
    private final IdempotencyKeys idempotency;
    private final ApplicationEventPublisher publisher;
    private final EntityManager entityManager;
    private final JsonMapper json;
    private final Clock clock;

    public ContractService(
            ContractRepository contracts,
            ContractEventRepository events,
            TenderLifecycle tenders,
            IdempotencyKeys idempotency,
            ApplicationEventPublisher publisher,
            EntityManager entityManager,
            JsonMapper json,
            Clock clock) {
        this.contracts = contracts;
        this.events = events;
        this.tenders = tenders;
        this.idempotency = idempotency;
        this.publisher = publisher;
        this.entityManager = entityManager;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ContractDetails> forTender(UUID tenderId) {
        TenderRef tender = tender(tenderId);
        boolean canEdit = CurrentUser.get().canEditOwnedBy(tender.ownerId());
        return contracts.findByTenderIdOrderBySignedAtAscCreatedAtAsc(tenderId).stream()
                .map(c -> ContractDetails.of(c, canEdit))
                .toList();
    }

    @Transactional(readOnly = true)
    public ContractDetails get(UUID id) {
        Contract contract = load(id);
        return details(contract, tender(contract.getTenderId()));
    }

    @Transactional(readOnly = true)
    public List<ContractEvent> history(UUID tenderId) {
        tender(tenderId);
        return events.findByTenderIdOrderByOccurredAtDesc(tenderId);
    }

    /** Registra um contrato assinado, com ou sem itens. O primeiro move a licitação para "Contrato assinado". */
    public ContractDetails sign(UUID tenderId, ContractDraft draft, List<ItemDraft> items) {
        CurrentUser user = CurrentUser.get();
        TenderRef tender = tender(tenderId);
        user.requireCanEditOwnedBy(tender.ownerId());
        if (!tender.acceptsContracts()) {
            throw new DomainRuleViolation(
                    "TENDER_NOT_CONTRACTABLE",
                    "Só uma licitação vencida e ainda não encerrada pode receber contratos.");
        }
        Contract contract = Contract.sign(tenderId, draft, tender.wonAt(), today());
        if (items != null && !items.isEmpty()) {
            contract.replaceItems(items);
        }
        contracts.save(contract);
        tenders.contractSigned(tenderId, contract.getSignedAt());
        record(contract, ContractEvent.Type.SIGNED, contract.getSignedAt(), signedSummary(contract), null, user);
        publisher.publishEvent(
                new ContractSigned(contract.getId(), tenderId, contract.getSignedAt(), contract.getValue()));
        contracts.flush();
        return ContractDetails.of(contract, true);
    }

    public ContractDetails update(UUID id, long expectedVersion, ContractDraft draft) {
        CurrentUser user = CurrentUser.get();
        Contract contract = load(id);
        TenderRef tender = editable(contract, expectedVersion, user);
        Map<String, String> before = contract.snapshot();
        contract.update(draft, tender.wonAt(), today());
        List<FieldChange> changes = diff(before, contract.snapshot());
        if (!changes.isEmpty()) {
            record(contract, ContractEvent.Type.UPDATED, null, null, changes, user);
        }
        contracts.flush();
        return details(contract, tender);
    }

    public ContractDetails replaceItems(UUID id, long expectedVersion, List<ItemDraft> items) {
        CurrentUser user = CurrentUser.get();
        Contract contract = load(id);
        TenderRef tender = editable(contract, expectedVersion, user);
        forceNewVersion(contract);
        contract.replaceItems(items);
        record(
                contract,
                ContractEvent.Type.ITEMS_CHANGED,
                null,
                items.size() + (items.size() == 1 ? " item" : " itens") + " · total " + money(contract.itemsTotal()),
                null,
                user);
        contracts.flush();
        return details(contract, tender);
    }

    /**
     * Registra um pedido. Com Idempotency-Key, repetir a mesma requisição devolve o contrato sem lançar o pedido de
     * novo (o caso de quem clica duas vezes ou perde a resposta).
     */
    public ContractDetails placeOrder(UUID id, long expectedVersion, OrderDraft draft, String idempotencyKey) {
        CurrentUser user = CurrentUser.get();
        Contract contract = load(id);
        if (idempotency.find(ORDER_SCOPE, idempotencyKey, user.id()).isPresent()) {
            return details(contract, tender(contract.getTenderId()));
        }
        TenderRef tender = editable(contract, expectedVersion, user);
        forceNewVersion(contract);
        ContractOrder order = contract.placeOrder(draft, today(), clock.instant(), user.name());
        tenders.orderRegistered(contract.getTenderId(), order.getOrderedAt());
        String summary = "Pedido de " + money(order.getValue())
                + (order.getReference() == null ? "" : " · " + order.getReference());
        record(contract, ContractEvent.Type.ORDER_PLACED, order.getOrderedAt(), summary, null, user);
        idempotency.remember(ORDER_SCOPE, idempotencyKey, user.id(), order.getId());
        publisher.publishEvent(new OrderRegistered(
                contract.getId(),
                contract.getTenderId(),
                order.getId(),
                order.getOrderedAt(),
                order.getValue(),
                contract.balance().remaining()));
        contracts.flush();
        return details(contract, tender);
    }

    public ContractDetails removeOrder(UUID id, long expectedVersion, UUID orderId) {
        CurrentUser user = CurrentUser.get();
        Contract contract = load(id);
        TenderRef tender = editable(contract, expectedVersion, user);
        forceNewVersion(contract);
        ContractOrder order = contract.removeOrder(orderId);
        record(
                contract,
                ContractEvent.Type.ORDER_REMOVED,
                order.getOrderedAt(),
                "Pedido de " + money(order.getValue()) + " apagado",
                null,
                user);
        contracts.flush();
        return details(contract, tender);
    }

    /** Encerra o contrato. Quando não sobra contrato aberto, a licitação encerra junto. */
    public ContractDetails close(UUID id, long expectedVersion, LocalDate date) {
        CurrentUser user = CurrentUser.get();
        Contract contract = load(id);
        TenderRef tender = editable(contract, expectedVersion, user);
        contract.close(date, today());
        record(
                contract,
                ContractEvent.Type.CLOSED,
                date,
                "Encerrado com saldo de " + money(contract.balance().remaining()),
                null,
                user);
        contracts.flush();
        if (!contracts.existsByTenderIdAndClosedAtIsNull(contract.getTenderId())) {
            tenders.allContractsClosed(contract.getTenderId(), contracts.lastClosedAt(contract.getTenderId()));
        }
        publisher.publishEvent(new ContractClosed(
                contract.getId(),
                contract.getTenderId(),
                date,
                contract.balance().remaining()));
        return details(contract, tender);
    }

    private Contract load(UUID id) {
        return contracts
                .findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Contrato não encontrado."));
    }

    private TenderRef tender(UUID tenderId) {
        return tenders.find(tenderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Licitação não encontrada."));
    }

    /** Confere permissão (pelo responsável da licitação) e versão; devolve a licitação. */
    private TenderRef editable(Contract contract, long expectedVersion, CurrentUser user) {
        TenderRef tender = tender(contract.getTenderId());
        user.requireCanEditOwnedBy(tender.ownerId());
        if (contract.getVersion() == null || contract.getVersion() != expectedVersion) {
            throw new ApiException(
                    ErrorCode.VERSION_CONFLICT,
                    "Outra pessoa alterou este contrato enquanto você editava. Recarregue para ver a versão atual.");
        }
        return tender;
    }

    /**
     * Sobe a versão do contrato na hora e trava a linha antes de mexer em itens ou pedidos: dois pedidos simultâneos
     * não passam pela mesma checagem de saldo (o segundo recebe 409), e a resposta já sai com a versão nova.
     */
    private void forceNewVersion(Contract contract) {
        entityManager.lock(contract, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    }

    private ContractDetails details(Contract contract, TenderRef tender) {
        return ContractDetails.of(contract, CurrentUser.get().canEditOwnedBy(tender.ownerId()));
    }

    private String signedSummary(Contract c) {
        return c.getKind().label() + (c.getNumber() == null ? "" : " " + c.getNumber()) + " de " + money(c.getValue());
    }

    private List<FieldChange> diff(Map<String, String> before, Map<String, String> after) {
        List<FieldChange> changes = new ArrayList<>();
        for (Map.Entry<String, String> entry : after.entrySet()) {
            String old = before.get(entry.getKey());
            if (!Objects.equals(old, entry.getValue())) {
                changes.add(new FieldChange(entry.getKey(), old, entry.getValue()));
            }
        }
        return changes;
    }

    private void record(
            Contract contract,
            ContractEvent.Type type,
            LocalDate date,
            String summary,
            List<FieldChange> changes,
            CurrentUser user) {
        events.save(new ContractEvent(
                contract.getId(),
                contract.getTenderId(),
                type,
                date,
                summary,
                changes == null ? null : json.writeValueAsString(changes),
                user.id(),
                user.name(),
                clock.instant()));
    }

    private static String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
                .format(value)
                .replace(' ', ' ');
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
