package br.com.shema.crm.contracts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ItemImportPlanTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    private static Contract contract() {
        return Contract.sign(
                UUID.randomUUID(),
                new ContractDraft(
                        ContractKind.CONTRATO, null, LocalDate.of(2026, 3, 1), null, new BigDecimal("237000"), null),
                null,
                TODAY);
    }

    private static ItemImportPlan.Input row(
            int n, String lot, String description, String qty, String price, String ordered) {
        return new ItemImportPlan.Input(
                n,
                lot,
                description,
                null,
                new BigDecimal(qty),
                new BigDecimal(price),
                ordered == null ? null : new BigDecimal(ordered));
    }

    /** Aplica o plano como o serviço faz: itens, depois o pedido da diferença. */
    private static void apply(Contract c, ItemImportPlan plan) {
        c.replaceItems(plan.items());
        OrderDraft.Line[] lines = plan.orderLines(c);
        if (lines.length > 0) {
            c.placeOrder(new OrderDraft(TODAY, "Importação", null, null, List.of(lines)), TODAY, Instant.now(), "E");
        }
    }

    @Test
    void firstImportCreatesItemsAndOrdersWhatTheSheetSaysWasOrdered() {
        Contract c = contract();
        ItemImportPlan plan = ItemImportPlan.of(
                c,
                List.of(
                        row(6, null, "CARTÕES DE VISITA 0.09X0.05 CM", "4800", "0.2", "1600"),
                        row(7, null, "PRISMA DE MESA", "240", "19", "0"),
                        // dois itens iguais na planilha da SEFAZ, com quantidades diferentes
                        row(9, null, "ADESIVO VEÍCULO 29,7X42,0 CM", "24", "18", null),
                        row(10, null, "ADESIVO VEÍCULO 29,7X42,0 CM", "240", "18", "24")));

        assertThat(plan.rows()).extracting(ItemImportPlan.Row::action).containsOnly(ItemImportPlan.Action.CREATE);
        apply(c, plan);

        assertThat(c.getItems()).hasSize(4);
        ContractBalance b = c.balance();
        assertThat(b.ordered()).isEqualByComparingTo("752.00"); // 1600 × 0,20 + 24 × 18
        assertThat(b.items().get(3).ordered()).isEqualByComparingTo("24");
    }

    @Test
    void reimportingTheSameSheetChangesNothing() {
        Contract c = contract();
        List<ItemImportPlan.Input> sheet = List.of(
                row(6, null, "Cartões de visita", "4800", "0.2", "1600"),
                row(9, null, "Adesivo veículo", "24", "18", null),
                row(10, null, "Adesivo veículo", "240", "18", "24"));
        apply(c, ItemImportPlan.of(c, sheet));

        ItemImportPlan again = ItemImportPlan.of(c, sheet);
        assertThat(again.rows()).extracting(ItemImportPlan.Row::action).containsOnly(ItemImportPlan.Action.KEEP);
        assertThat(again.changesItems()).isFalse();
        assertThat(again.orderLines(c)).isEmpty();
    }

    @Test
    void matchesIgnoringAccentsCaseAndSpacesAndOrdersOnlyTheDifference() {
        Contract c = contract();
        apply(c, ItemImportPlan.of(c, List.of(row(6, "Lote 1", "Colete  SEGOV", "520", "90.33", "100"))));

        ItemImportPlan next = ItemImportPlan.of(c, List.of(row(6, "lote 1", "colete segov", "600", "90.33", "250")));
        assertThat(next.rows()).singleElement().satisfies(r -> {
            assertThat(r.action()).isEqualTo(ItemImportPlan.Action.UPDATE);
            assertThat(r.newOrderQuantity()).isEqualByComparingTo("150");
        });
        apply(c, next);
        assertThat(c.getItems()).hasSize(1);
        assertThat(c.getItems().getFirst().getQuantity()).isEqualByComparingTo("600");
        assertThat(c.balance().items().getFirst().ordered()).isEqualByComparingTo("250");
    }

    @Test
    void neverRemovesOrdersOrShrinksBelowWhatWasOrdered() {
        Contract c = contract();
        apply(c, ItemImportPlan.of(c, List.of(row(6, null, "Banner", "100", "75", "40"))));

        ItemImportPlan lessOrdered = ItemImportPlan.of(c, List.of(row(6, null, "Banner", "100", "75", "30")));
        assertThat(lessOrdered.rows().getFirst().action()).isEqualTo(ItemImportPlan.Action.WARN);
        assertThat(lessOrdered.rows().getFirst().message()).contains("nenhum pedido foi apagado");
        assertThat(lessOrdered.orderLines(c)).isEmpty();

        ItemImportPlan shrink = ItemImportPlan.of(c, List.of(row(6, null, "Banner", "39", "75", null)));
        assertThat(shrink.rows().getFirst().action()).isEqualTo(ItemImportPlan.Action.WARN);
        assertThat(shrink.items().getFirst().quantity()).isEqualByComparingTo("100");
    }

    @Test
    void orderAboveTheContractedQuantityIsAWarning() {
        Contract c = contract();
        ItemImportPlan plan = ItemImportPlan.of(c, List.of(row(6, null, "Banner", "10", "75", "11")));
        assertThat(plan.rows().getFirst().action()).isEqualTo(ItemImportPlan.Action.WARN);
        assertThat(plan.orderLines(c)).isEmpty();
    }

    @Test
    void itemsNotInTheSheetStay() {
        Contract c = contract();
        apply(
                c,
                ItemImportPlan.of(
                        c, List.of(row(6, null, "Banner", "10", "75", null), row(7, null, "Faixa", "5", "20", null))));
        ItemImportPlan plan = ItemImportPlan.of(c, List.of(row(6, null, "Banner", "10", "75", null)));
        assertThat(plan.items()).extracting(ItemDraft::description).containsExactly("Banner", "Faixa");
    }
}
