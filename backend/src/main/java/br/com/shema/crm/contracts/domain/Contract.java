package br.com.shema.crm.contracts.domain;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.domain.Money;
import br.com.shema.crm.shared.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Contrato (ou empenho, ou dispensa) de uma licitação, com seus itens e pedidos. É um agregado só: o pedido é
 * validado contra o saldo aqui dentro, e a versão do contrato impede que dois pedidos simultâneos estourem o saldo.
 */
@Entity
@Table(name = "contracts")
public class Contract extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "tender_id", nullable = false)
    private UUID tenderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContractKind kind;

    @Column(length = 40)
    private String number;

    @Column(name = "signed_at", nullable = false)
    private LocalDate signedAt;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "closed_at")
    private LocalDate closedAt;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal value;

    @Column(length = 4000)
    private String notes;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "contract_id", nullable = false)
    @OrderBy("position")
    private List<ContractItem> items = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "contract_id", nullable = false)
    @OrderBy("orderedAt, createdAt")
    private List<ContractOrder> orders = new ArrayList<>();

    protected Contract() {}

    /**
     * Registra um contrato assinado.
     *
     * @param wonAt data em que a licitação foi vencida: a assinatura não pode ser antes
     */
    public static Contract sign(UUID tenderId, ContractDraft draft, LocalDate wonAt, LocalDate today) {
        Contract contract = new Contract();
        contract.id = UUID.randomUUID();
        contract.tenderId = Objects.requireNonNull(tenderId, "tenderId");
        contract.apply(draft, wonAt, today);
        return contract;
    }

    public void update(ContractDraft draft, LocalDate wonAt, LocalDate today) {
        apply(draft, wonAt, today);
    }

    /**
     * Substitui a lista de itens: os que vêm com id são alterados, os sem id são criados e os que não vêm são
     * removidos. Item com pedido não pode ser removido nem ficar com quantidade menor que a já pedida.
     */
    public void replaceItems(List<ItemDraft> drafts) {
        Map<UUID, BigDecimal> orderedQty = orderedQuantityByItem();
        Map<UUID, ContractItem> current =
                items.stream().collect(Collectors.toMap(ContractItem::getId, Function.identity()));
        Set<UUID> kept = new HashSet<>();
        List<ContractItem> result = new ArrayList<>();
        int position = 0;
        for (ItemDraft draft : drafts) {
            ContractItem item;
            if (draft.id() == null) {
                item = new ContractItem(position, draft);
            } else {
                item = current.get(draft.id());
                if (item == null || !kept.add(draft.id())) {
                    throw new DomainRuleViolation("ITEM_NOT_FOUND", "Item não encontrado neste contrato.");
                }
                item.apply(draft);
                item.moveTo(position);
                BigDecimal ordered = orderedQty.getOrDefault(item.getId(), BigDecimal.ZERO);
                if (item.getQuantity().compareTo(ordered) < 0) {
                    throw new DomainRuleViolation(
                            "QUANTITY_BELOW_ORDERED",
                            "\"" + item.getDescription() + "\" já tem " + BrFormat.quantity(ordered)
                                    + " pedidos; a quantidade contratada não pode ser menor.");
                }
            }
            result.add(item);
            position++;
        }
        for (ContractItem old : items) {
            if (!kept.contains(old.getId()) && orderedQty.containsKey(old.getId())) {
                throw new DomainRuleViolation(
                        "ITEM_HAS_ORDERS", "\"" + old.getDescription() + "\" já tem pedidos e não pode ser removido.");
            }
        }
        items.clear();
        items.addAll(result);
    }

    /** Registra um pedido, validando data e saldo. */
    public ContractOrder placeOrder(OrderDraft draft, LocalDate today, Instant now, String userName) {
        requireOpen();
        LocalDate date = draft.orderedAt();
        if (date == null) {
            throw new DomainRuleViolation("DATE_REQUIRED", "Informe a data do pedido.");
        }
        if (date.isAfter(today)) {
            throw new DomainRuleViolation("DATE_IN_FUTURE", "A data do pedido não pode ser no futuro.");
        }
        if (date.isBefore(signedAt)) {
            throw new DomainRuleViolation("DATE_ORDER", "O pedido não pode ser antes da assinatura do contrato.");
        }
        ContractBalance balance = balance();
        List<OrderLine> lines = new ArrayList<>();
        BigDecimal orderValue;
        if (items.isEmpty()) {
            if (!draft.lines().isEmpty()) {
                throw new DomainRuleViolation(
                        "ITEM_NOT_FOUND", "Este contrato não tem itens: informe só o valor do pedido.");
            }
            if (draft.value() == null || draft.value().signum() <= 0) {
                throw new DomainRuleViolation("ORDER_VALUE_REQUIRED", "Informe o valor do pedido.");
            }
            orderValue = Money.of(draft.value()).amount();
        } else {
            if (draft.lines().isEmpty()) {
                throw new DomainRuleViolation("ORDER_EMPTY", "Informe a quantidade pedida de pelo menos um item.");
            }
            Map<UUID, ContractItem> byId =
                    items.stream().collect(Collectors.toMap(ContractItem::getId, Function.identity()));
            Map<UUID, ContractBalance.ItemBalance> itemBalances = balance.items().stream()
                    .collect(Collectors.toMap(ContractBalance.ItemBalance::itemId, Function.identity()));
            Set<UUID> seen = new HashSet<>();
            for (OrderDraft.Line line : draft.lines()) {
                ContractItem item = byId.get(line.itemId());
                if (item == null) {
                    throw new DomainRuleViolation("ITEM_NOT_FOUND", "Item não encontrado neste contrato.");
                }
                if (!seen.add(item.getId())) {
                    throw new DomainRuleViolation(
                            "DUPLICATE_ITEM", "\"" + item.getDescription() + "\" aparece duas vezes no pedido.");
                }
                BigDecimal quantity = Quantities.quantity(line.quantity(), "pedida");
                BigDecimal remaining = itemBalances.get(item.getId()).remaining();
                if (quantity.compareTo(remaining) > 0) {
                    throw new DomainRuleViolation(
                            "BALANCE_EXCEEDED",
                            "\"" + item.getDescription() + "\": o saldo é de " + BrFormat.quantity(remaining)
                                    + " e o pedido é de " + BrFormat.quantity(quantity) + ".");
                }
                lines.add(new OrderLine(item.getId(), quantity, item.getUnitPrice()));
            }
            orderValue = lines.stream().map(OrderLine::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        if (orderValue.compareTo(balance.remaining()) > 0) {
            throw new DomainRuleViolation(
                    "BALANCE_EXCEEDED",
                    "O pedido de " + BrFormat.money(orderValue) + " passa do saldo do contrato, que é de "
                            + BrFormat.money(balance.remaining()) + ".");
        }
        ContractOrder order = new ContractOrder(
                date, blankToNull(draft.reference()), blankToNull(draft.notes()), orderValue, lines, now, userName);
        orders.add(order);
        return order;
    }

    /** Apaga um pedido lançado por engano. O saldo volta. */
    public ContractOrder removeOrder(UUID orderId) {
        requireOpen();
        ContractOrder order = orders.stream()
                .filter(o -> o.getId().equals(orderId))
                .findFirst()
                .orElseThrow(() -> new DomainRuleViolation("ORDER_NOT_FOUND", "Pedido não encontrado neste contrato."));
        orders.remove(order);
        return order;
    }

    /** Encerra o contrato. O saldo que sobrou fica registrado; não é preciso ter pedido tudo. */
    public void close(LocalDate date, LocalDate today) {
        requireOpen();
        if (date == null) {
            throw new DomainRuleViolation("DATE_REQUIRED", "Informe a data de encerramento.");
        }
        if (date.isAfter(today)) {
            throw new DomainRuleViolation("DATE_IN_FUTURE", "A data de encerramento não pode ser no futuro.");
        }
        if (date.isBefore(signedAt)) {
            throw new DomainRuleViolation("DATE_ORDER", "O encerramento não pode ser antes da assinatura.");
        }
        LocalDate lastOrder = lastOrderDate();
        if (lastOrder != null && date.isBefore(lastOrder)) {
            throw new DomainRuleViolation("DATE_ORDER", "O encerramento não pode ser antes do último pedido.");
        }
        closedAt = date;
    }

    public ContractBalance balance() {
        Map<UUID, BigDecimal> orderedQty = orderedQuantityByItem();
        Map<UUID, BigDecimal> orderedValue = new HashMap<>();
        for (ContractOrder order : orders) {
            for (OrderLine line : order.getLines()) {
                orderedValue.merge(line.getItemId(), line.getTotal(), BigDecimal::add);
            }
        }
        List<ContractBalance.ItemBalance> itemBalances = items.stream()
                .map(item -> {
                    BigDecimal qty = orderedQty.getOrDefault(item.getId(), BigDecimal.ZERO.setScale(3));
                    BigDecimal val = orderedValue.getOrDefault(item.getId(), BigDecimal.ZERO.setScale(2));
                    return new ContractBalance.ItemBalance(
                            item.getId(),
                            item.getQuantity(),
                            qty,
                            item.getQuantity().subtract(qty),
                            val,
                            item.total().subtract(val).max(BigDecimal.ZERO.setScale(2)));
                })
                .toList();
        BigDecimal ordered =
                orders.stream().map(ContractOrder::getValue).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        return new ContractBalance(value, ordered, value.subtract(ordered), itemBalances);
    }

    /** Soma dos itens (quantidade × valor unitário). Pode diferir do valor do contrato por arredondamento. */
    public BigDecimal itemsTotal() {
        return items.stream().map(ContractItem::total).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    public boolean isClosed() {
        return closedAt != null;
    }

    public LocalDate firstOrderDate() {
        return orders.stream()
                .map(ContractOrder::getOrderedAt)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    public LocalDate lastOrderDate() {
        return orders.stream()
                .map(ContractOrder::getOrderedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    /** Fotografia dos campos editáveis, para o histórico mostrar o que mudou. */
    public Map<String, String> snapshot() {
        Map<String, String> s = new LinkedHashMap<>();
        s.put("Tipo", kind.label());
        s.put("Número", number);
        s.put("Assinatura", signedAt.toString());
        s.put("Vigência até", validUntil == null ? null : validUntil.toString());
        s.put("Valor", value.toPlainString());
        s.put("Observações", notes);
        return s;
    }

    private void apply(ContractDraft d, LocalDate wonAt, LocalDate today) {
        this.kind = d.kind() == null ? ContractKind.CONTRATO : d.kind();
        this.number = blankToNull(d.number());
        if (d.signedAt() == null) {
            throw new DomainRuleViolation("DATE_REQUIRED", "Informe a data de assinatura.");
        }
        if (d.signedAt().isAfter(today)) {
            throw new DomainRuleViolation("DATE_IN_FUTURE", "A data de assinatura não pode ser no futuro.");
        }
        if (wonAt != null && d.signedAt().isBefore(wonAt)) {
            throw new DomainRuleViolation("DATE_ORDER", "A data de assinatura não pode ser antes da data da vitória.");
        }
        LocalDate firstOrder = firstOrderDate();
        if (firstOrder != null && d.signedAt().isAfter(firstOrder)) {
            throw new DomainRuleViolation("DATE_ORDER", "A assinatura não pode ser depois do primeiro pedido.");
        }
        if (d.validUntil() != null && d.validUntil().isBefore(d.signedAt())) {
            throw new DomainRuleViolation("VALIDITY_ORDER", "A vigência não pode terminar antes da assinatura.");
        }
        if (d.value() == null) {
            throw new DomainRuleViolation("REQUIRED", "Informe o valor do contrato.");
        }
        BigDecimal newValue = Money.of(d.value()).amount();
        BigDecimal ordered = orders.stream().map(ContractOrder::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (newValue.compareTo(ordered) < 0) {
            throw new DomainRuleViolation(
                    "VALUE_BELOW_ORDERED",
                    "Já foram pedidos " + BrFormat.money(ordered) + "; o valor do contrato não pode ser menor.");
        }
        this.signedAt = d.signedAt();
        this.validUntil = d.validUntil();
        this.value = newValue;
        this.notes = blankToNull(d.notes());
    }

    private Map<UUID, BigDecimal> orderedQuantityByItem() {
        Map<UUID, BigDecimal> qty = new HashMap<>();
        for (ContractOrder order : orders) {
            for (OrderLine line : order.getLines()) {
                qty.merge(line.getItemId(), line.getQuantity(), BigDecimal::add);
            }
        }
        return qty;
    }

    private void requireOpen() {
        if (closedAt != null) {
            throw new DomainRuleViolation("CONTRACT_CLOSED", "Este contrato já foi encerrado.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenderId() {
        return tenderId;
    }

    public ContractKind getKind() {
        return kind;
    }

    public String getNumber() {
        return number;
    }

    public LocalDate getSignedAt() {
        return signedAt;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public LocalDate getClosedAt() {
        return closedAt;
    }

    public BigDecimal getValue() {
        return value;
    }

    public String getNotes() {
        return notes;
    }

    public List<ContractItem> getItems() {
        return List.copyOf(items);
    }

    public List<ContractOrder> getOrders() {
        return List.copyOf(orders);
    }
}
