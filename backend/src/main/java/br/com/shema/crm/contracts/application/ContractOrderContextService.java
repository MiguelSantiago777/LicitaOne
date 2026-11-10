package br.com.shema.crm.contracts.application;

import br.com.shema.crm.contracts.ContractOrderContext;
import br.com.shema.crm.contracts.domain.Contract;
import br.com.shema.crm.contracts.domain.ContractBalance;
import br.com.shema.crm.contracts.domain.ContractOrder;
import br.com.shema.crm.contracts.infrastructure.ContractRepository;
import br.com.shema.crm.tenders.TenderLifecycle;
import br.com.shema.crm.tenders.TenderRef;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class ContractOrderContextService implements ContractOrderContext {

    private final ContractRepository contracts;
    private final TenderLifecycle tenders;

    ContractOrderContextService(ContractRepository contracts, TenderLifecycle tenders) {
        this.contracts = contracts;
        this.tenders = tenders;
    }

    @Override
    public Optional<OrderContext> find(UUID contractId) {
        return contracts
                .findById(contractId)
                .flatMap(c -> tenders.find(c.getTenderId()).map(t -> context(c, t)));
    }

    @Override
    public List<OrderContext> allOpen() {
        List<Contract> open = contracts.findByClosedAtIsNull();
        Map<UUID, TenderRef> byTender =
                tenders.findAll(open.stream().map(Contract::getTenderId).collect(Collectors.toSet()));
        return open.stream()
                .filter(c -> byTender.containsKey(c.getTenderId()))
                .map(c -> context(c, byTender.get(c.getTenderId())))
                .toList();
    }

    @Override
    public List<OrderContext> openForTender(UUID tenderId) {
        return tenders.find(tenderId)
                .map(t -> contracts.findByTenderIdOrderBySignedAtAscCreatedAtAsc(tenderId).stream()
                        .filter(c -> !c.isClosed())
                        .map(c -> context(c, t))
                        .toList())
                .orElse(List.of());
    }

    private static OrderContext context(Contract c, TenderRef t) {
        ContractBalance balance = c.balance();
        Map<UUID, ContractBalance.ItemBalance> balances = balance.items().stream()
                .collect(Collectors.toMap(ContractBalance.ItemBalance::itemId, Function.identity()));
        LocalDate lastOrderAt = c.getOrders().stream()
                .map(ContractOrder::getOrderedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new OrderContext(
                c.getId(),
                t.id(),
                t.ownerId(),
                t.agencyName() + " · " + c.getKind().label() + (c.getNumber() == null ? "" : " " + c.getNumber()),
                t.agencyName(),
                c.getKind().label(),
                c.getNumber(),
                c.getSignedAt(),
                c.getValidUntil(),
                c.isClosed(),
                balance.value(),
                balance.ordered(),
                balance.remaining(),
                lastOrderAt,
                c.getItems().stream()
                        .map(i -> new Item(
                                i.getId(),
                                i.getLot(),
                                i.getDescription(),
                                i.getUnit(),
                                i.getQuantity(),
                                i.getUnitPrice(),
                                balances.get(i.getId()).remaining()))
                        .toList());
    }
}
