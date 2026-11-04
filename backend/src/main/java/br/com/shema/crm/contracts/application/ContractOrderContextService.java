package br.com.shema.crm.contracts.application;

import br.com.shema.crm.contracts.ContractOrderContext;
import br.com.shema.crm.contracts.domain.Contract;
import br.com.shema.crm.contracts.domain.ContractBalance;
import br.com.shema.crm.contracts.infrastructure.ContractRepository;
import br.com.shema.crm.tenders.TenderLifecycle;
import br.com.shema.crm.tenders.TenderRef;
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

    private static OrderContext context(Contract c, TenderRef t) {
        Map<UUID, ContractBalance.ItemBalance> balances = c.balance().items().stream()
                .collect(Collectors.toMap(ContractBalance.ItemBalance::itemId, Function.identity()));
        return new OrderContext(
                c.getId(),
                t.id(),
                t.ownerId(),
                t.agencyName() + " · " + c.getKind().label() + (c.getNumber() == null ? "" : " " + c.getNumber()),
                t.agencyName(),
                c.getKind().label(),
                c.getNumber(),
                c.getSignedAt(),
                c.isClosed(),
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
