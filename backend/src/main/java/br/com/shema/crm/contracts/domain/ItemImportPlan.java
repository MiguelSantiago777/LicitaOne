package br.com.shema.crm.contracts.domain;

import br.com.shema.crm.shared.domain.BrFormat;
import br.com.shema.crm.shared.domain.Quantities;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Como as linhas da planilha entram num contrato, calculado sem efeito colateral (a prévia e a gravação usam o mesmo
 * plano).
 *
 * <ul>
 *   <li>Uma linha casa com um item existente de mesmo lote, descrição (sem acento, caixa e espaços extras) e valor
 *       unitário. Linhas repetidas casam com itens repetidos na ordem em que aparecem (a planilha da SEFAZ tem dois
 *       "Adesivo veículo" iguais com quantidades diferentes).
 *   <li>Item casado com quantidade diferente é atualizado; sem casamento, é criado. Itens do contrato que não estão na
 *       planilha ficam como estão.
 *   <li>O pedido novo é a diferença entre o "pedido" da planilha e o já pedido no sistema. Se a planilha tiver menos
 *       que o sistema, nada é apagado: a linha vira aviso.
 * </ul>
 */
public final class ItemImportPlan {

    public enum Action {
        CREATE,
        UPDATE,
        KEEP,
        WARN
    }

    /** @param newOrderQuantity quanto entra no pedido novo (zero se nada) */
    public record Row(int rowNumber, Action action, String message, int itemIndex, BigDecimal newOrderQuantity) {}

    public record Input(
            int rowNumber,
            String lot,
            String description,
            String unit,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal orderedQuantity) {}

    private final List<ItemDraft> items;
    private final List<Row> rows;

    private ItemImportPlan(List<ItemDraft> items, List<Row> rows) {
        this.items = items;
        this.rows = rows;
    }

    public static ItemImportPlan of(Contract contract, List<Input> inputs) {
        ContractBalance balance = contract.balance();
        Map<UUID, BigDecimal> ordered = new HashMap<>();
        balance.items().forEach(b -> ordered.put(b.itemId(), b.ordered()));

        List<ItemDraft> drafts = new ArrayList<>();
        Map<String, Deque<Integer>> available = new LinkedHashMap<>();
        for (ContractItem item : contract.getItems()) {
            drafts.add(new ItemDraft(
                    item.getId(),
                    item.getLot(),
                    item.getDescription(),
                    item.getUnit(),
                    item.getQuantity(),
                    item.getUnitPrice()));
            available
                    .computeIfAbsent(
                            key(item.getLot(), item.getDescription(), item.getUnitPrice()), k -> new ArrayDeque<>())
                    .add(drafts.size() - 1);
        }

        List<Row> rows = new ArrayList<>();
        for (Input in : inputs) {
            BigDecimal quantity = Quantities.quantity(in.quantity(), "contratada");
            BigDecimal unitPrice = Quantities.unitPrice(in.unitPrice());
            Deque<Integer> candidates = available.get(key(in.lot(), in.description(), unitPrice));
            Integer index = candidates == null ? null : candidates.pollFirst();
            Action action;
            String message = null;
            BigDecimal alreadyOrdered = BigDecimal.ZERO;
            if (index == null) {
                drafts.add(new ItemDraft(null, in.lot(), in.description(), in.unit(), quantity, unitPrice));
                index = drafts.size() - 1;
                action = Action.CREATE;
            } else {
                ItemDraft current = drafts.get(index);
                alreadyOrdered = ordered.getOrDefault(current.id(), BigDecimal.ZERO);
                if (current.quantity().compareTo(quantity) == 0) {
                    action = Action.KEEP;
                } else if (quantity.compareTo(alreadyOrdered) < 0) {
                    action = Action.WARN;
                    message = "A planilha tem " + BrFormat.quantity(quantity) + " contratados, mas já foram pedidos "
                            + BrFormat.quantity(alreadyOrdered) + "; a quantidade não foi alterada.";
                } else {
                    drafts.set(
                            index,
                            new ItemDraft(
                                    current.id(),
                                    current.lot(),
                                    current.description(),
                                    current.unit(),
                                    quantity,
                                    unitPrice));
                    action = Action.UPDATE;
                    message =
                            "Quantidade " + BrFormat.quantity(current.quantity()) + " → " + BrFormat.quantity(quantity);
                }
            }

            BigDecimal newOrder = BigDecimal.ZERO;
            if (in.orderedQuantity() != null && in.orderedQuantity().signum() > 0) {
                BigDecimal sheetOrdered = in.orderedQuantity().setScale(3, RoundingMode.HALF_UP);
                BigDecimal itemQuantity = drafts.get(index).quantity();
                if (sheetOrdered.compareTo(itemQuantity) > 0) {
                    action = Action.WARN;
                    message = "Pedido (" + BrFormat.quantity(sheetOrdered) + ") maior que a quantidade contratada ("
                            + BrFormat.quantity(itemQuantity) + "); o pedido desta linha não foi lançado.";
                } else if (sheetOrdered.compareTo(alreadyOrdered) > 0) {
                    newOrder = sheetOrdered.subtract(alreadyOrdered);
                } else if (sheetOrdered.compareTo(alreadyOrdered) < 0) {
                    action = Action.WARN;
                    message = "A planilha tem " + BrFormat.quantity(sheetOrdered) + " pedidos e o sistema já tem "
                            + BrFormat.quantity(alreadyOrdered) + "; nenhum pedido foi apagado.";
                }
            }
            rows.add(new Row(in.rowNumber(), action, message, index, newOrder));
        }
        return new ItemImportPlan(drafts, rows);
    }

    public List<Row> rows() {
        return List.copyOf(rows);
    }

    public List<ItemDraft> items() {
        return List.copyOf(items);
    }

    public boolean changesItems() {
        return rows.stream().anyMatch(r -> r.action() == Action.CREATE || r.action() == Action.UPDATE);
    }

    /** Linhas do pedido novo, depois que os itens foram gravados (os itens novos ganham id no contrato). */
    public OrderDraft.Line[] orderLines(Contract contractAfterItems) {
        List<ContractItem> saved = contractAfterItems.getItems();
        return rows.stream()
                .filter(r -> r.newOrderQuantity().signum() > 0)
                .map(r -> new OrderDraft.Line(saved.get(r.itemIndex()).getId(), r.newOrderQuantity()))
                .toArray(OrderDraft.Line[]::new);
    }

    private static String key(String lot, String description, BigDecimal unitPrice) {
        return normalize(lot) + "|" + normalize(description) + "|"
                + unitPrice.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String noAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
