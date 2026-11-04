package br.com.shema.crm.contracts.application;

import br.com.shema.crm.contracts.domain.Contract;
import br.com.shema.crm.contracts.domain.ContractBalance;
import br.com.shema.crm.contracts.domain.ContractItem;
import br.com.shema.crm.contracts.domain.ContractKind;
import br.com.shema.crm.contracts.domain.ContractOrder;
import br.com.shema.crm.contracts.domain.OrderLine;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Um contrato com itens, pedidos e saldo, montado dentro da transação (open-in-view desligado). É também a
 * resposta da API.
 */
@Schema(name = "ContractResponse")
public record ContractDetails(
        UUID id,
        UUID tenderId,
        ContractKind kind,
        String kindLabel,
        String number,
        LocalDate signedAt,
        LocalDate validUntil,
        LocalDate closedAt,
        BigDecimal value,
        BigDecimal itemsTotal,
        BigDecimal ordered,
        BigDecimal remaining,
        int percentOrdered,
        String notes,
        List<Item> items,
        List<Order> orders,
        boolean canEdit,
        long version) {

    @Schema(name = "ContractItemResponse")
    public record Item(
            UUID id,
            String lot,
            String description,
            String unit,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal total,
            BigDecimal ordered,
            BigDecimal remaining,
            BigDecimal remainingValue) {}

    @Schema(name = "ContractOrderResponse")
    public record Order(
            UUID id,
            LocalDate orderedAt,
            String reference,
            String notes,
            BigDecimal value,
            List<Line> lines,
            String createdByName,
            Instant createdAt,
            UUID sourceDocumentId) {}

    @Schema(name = "ContractOrderLineResponse")
    public record Line(UUID itemId, String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal total) {}

    static ContractDetails of(Contract c, boolean canEdit) {
        ContractBalance balance = c.balance();
        Map<UUID, ContractBalance.ItemBalance> byItem = balance.items().stream()
                .collect(Collectors.toMap(ContractBalance.ItemBalance::itemId, Function.identity()));
        Map<UUID, String> descriptions =
                c.getItems().stream().collect(Collectors.toMap(ContractItem::getId, ContractItem::getDescription));
        List<Item> items = c.getItems().stream()
                .map(i -> {
                    ContractBalance.ItemBalance b = byItem.get(i.getId());
                    return new Item(
                            i.getId(),
                            i.getLot(),
                            i.getDescription(),
                            i.getUnit(),
                            i.getQuantity(),
                            i.getUnitPrice(),
                            i.total(),
                            b.ordered(),
                            b.remaining(),
                            b.remainingValue());
                })
                .toList();
        List<Order> orders = c.getOrders().stream()
                .sorted((a, b) -> b.getOrderedAt().compareTo(a.getOrderedAt()))
                .map(o -> order(o, descriptions))
                .toList();
        return new ContractDetails(
                c.getId(),
                c.getTenderId(),
                c.getKind(),
                c.getKind().label(),
                c.getNumber(),
                c.getSignedAt(),
                c.getValidUntil(),
                c.getClosedAt(),
                c.getValue(),
                c.itemsTotal(),
                balance.ordered(),
                balance.remaining(),
                balance.percentOrdered(),
                c.getNotes(),
                items,
                orders,
                canEdit,
                c.getVersion() == null ? 0 : c.getVersion());
    }

    private static Order order(ContractOrder o, Map<UUID, String> descriptions) {
        List<Line> lines = o.getLines().stream()
                .map((OrderLine l) -> new Line(
                        l.getItemId(),
                        descriptions.getOrDefault(l.getItemId(), "Item removido"),
                        l.getQuantity(),
                        l.getUnitPrice(),
                        l.getTotal()))
                .toList();
        return new Order(
                o.getId(),
                o.getOrderedAt(),
                o.getReference(),
                o.getNotes(),
                o.getValue(),
                lines,
                o.getCreatedByName(),
                o.getCreatedAt(),
                o.getSourceDocumentId());
    }
}
