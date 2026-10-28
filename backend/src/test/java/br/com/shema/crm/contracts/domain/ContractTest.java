package br.com.shema.crm.contracts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContractTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final LocalDate WON = LocalDate.of(2026, 4, 10);
    private static final LocalDate SIGNED = LocalDate.of(2026, 5, 16);
    private static final Instant NOW = Instant.parse("2026-10-28T13:00:00Z");

    private static ContractDraft draft(String value) {
        return new ContractDraft(
                ContractKind.CONTRATO, "12/2026", SIGNED, LocalDate.of(2027, 5, 15), new BigDecimal(value), null);
    }

    private static ItemDraft item(String description, String quantity, String unitPrice) {
        return new ItemDraft(null, null, description, "un", new BigDecimal(quantity), new BigDecimal(unitPrice));
    }

    /** O exemplo da especificação: contrato de R$ 300.000 com banner e adesivo perfurado. */
    private static Contract withItems() {
        Contract c = Contract.sign(UUID.randomUUID(), draft("300000.00"), WON, TODAY);
        c.replaceItems(List.of(item("Banner", "2000", "100.00"), item("Adesivo perfurado", "4000", "25.00")));
        return c;
    }

    private static OrderDraft order(LocalDate date, Contract c, String... quantities) {
        List<OrderDraft.Line> lines = new java.util.ArrayList<>();
        for (int i = 0; i < quantities.length; i++) {
            if (quantities[i] != null) {
                lines.add(new OrderDraft.Line(c.getItems().get(i).getId(), new BigDecimal(quantities[i])));
            }
        }
        return new OrderDraft(date, "AF 123", null, null, lines);
    }

    private static void assertRule(Runnable action, String rule) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DomainRuleViolation.class)
                .extracting(e -> ((DomainRuleViolation) e).rule())
                .isEqualTo(rule);
    }

    @Test
    void signsWithTheRequiredDate() {
        Contract c = Contract.sign(UUID.randomUUID(), draft("89000"), WON, TODAY);
        assertThat(c.getSignedAt()).isEqualTo(SIGNED);
        assertThat(c.getValue()).isEqualByComparingTo("89000.00");
        assertThat(c.isClosed()).isFalse();

        ContractDraft noDate = new ContractDraft(ContractKind.EMPENHO, null, null, null, BigDecimal.TEN, null);
        assertRule(() -> Contract.sign(UUID.randomUUID(), noDate, WON, TODAY), "DATE_REQUIRED");
    }

    @Test
    void signatureCannotBeInTheFutureNorBeforeTheWin() {
        ContractDraft future =
                new ContractDraft(ContractKind.CONTRATO, null, TODAY.plusDays(1), null, BigDecimal.TEN, null);
        assertRule(() -> Contract.sign(UUID.randomUUID(), future, WON, TODAY), "DATE_IN_FUTURE");
        ContractDraft early =
                new ContractDraft(ContractKind.CONTRATO, null, WON.minusDays(1), null, BigDecimal.TEN, null);
        assertRule(() -> Contract.sign(UUID.randomUUID(), early, WON, TODAY), "DATE_ORDER");
        ContractDraft validity =
                new ContractDraft(ContractKind.CONTRATO, null, SIGNED, SIGNED.minusDays(1), BigDecimal.TEN, null);
        assertRule(() -> Contract.sign(UUID.randomUUID(), validity, WON, TODAY), "VALIDITY_ORDER");
    }

    @Test
    void balanceFollowsTheOrdersByValueAndByItem() {
        Contract c = withItems();
        c.placeOrder(order(LocalDate.of(2026, 6, 1), c, "800", "1600"), TODAY, NOW, "Edwin");

        ContractBalance b = c.balance();
        assertThat(b.ordered()).isEqualByComparingTo("120000.00");
        assertThat(b.remaining()).isEqualByComparingTo("180000.00");
        assertThat(b.percentOrdered()).isEqualTo(40);
        assertThat(b.items().get(0).remaining()).isEqualByComparingTo("1200");
        assertThat(b.items().get(1).remaining()).isEqualByComparingTo("2400");
        assertThat(b.items().get(1).orderedValue()).isEqualByComparingTo("40000.00");
    }

    @Test
    void orderCannotExceedTheItemBalance() {
        Contract c = withItems();
        c.placeOrder(order(LocalDate.of(2026, 6, 1), c, "1500"), TODAY, NOW, "Edwin");
        assertRule(
                () -> c.placeOrder(order(LocalDate.of(2026, 7, 1), c, "501"), TODAY, NOW, "Edwin"), "BALANCE_EXCEEDED");
        c.placeOrder(order(LocalDate.of(2026, 7, 1), c, "500"), TODAY, NOW, "Edwin");
        assertThat(c.balance().items().get(0).remaining()).isEqualByComparingTo("0");
    }

    @Test
    void orderCannotExceedTheContractValueEvenWhenItemsAddUpToMore() {
        Contract c = Contract.sign(UUID.randomUUID(), draft("1000.00"), WON, TODAY);
        c.replaceItems(List.of(item("Banner", "20", "100.00")));
        assertRule(
                () -> c.placeOrder(order(LocalDate.of(2026, 6, 1), c, "11"), TODAY, NOW, "Edwin"), "BALANCE_EXCEEDED");
    }

    @Test
    void contractWithoutItemsTakesOrdersByValue() {
        Contract c = Contract.sign(UUID.randomUUID(), draft("300000"), WON, TODAY);
        c.placeOrder(
                new OrderDraft(LocalDate.of(2026, 6, 1), null, "banner 2.000", new BigDecimal("120000"), null),
                TODAY,
                NOW,
                "Edwin");
        assertThat(c.balance().remaining()).isEqualByComparingTo("180000.00");
        assertRule(
                () -> c.placeOrder(
                        new OrderDraft(LocalDate.of(2026, 6, 2), null, null, new BigDecimal("180000.01"), null),
                        TODAY,
                        NOW,
                        "Edwin"),
                "BALANCE_EXCEEDED");
        assertRule(
                () -> c.placeOrder(new OrderDraft(LocalDate.of(2026, 6, 2), null, null, null, null), TODAY, NOW, "E"),
                "ORDER_VALUE_REQUIRED");
    }

    @Test
    void orderRules() {
        Contract c = withItems();
        assertRule(() -> c.placeOrder(order(null, c, "1"), TODAY, NOW, "E"), "DATE_REQUIRED");
        assertRule(() -> c.placeOrder(order(TODAY.plusDays(1), c, "1"), TODAY, NOW, "E"), "DATE_IN_FUTURE");
        assertRule(() -> c.placeOrder(order(SIGNED.minusDays(1), c, "1"), TODAY, NOW, "E"), "DATE_ORDER");
        assertRule(() -> c.placeOrder(order(SIGNED, c), TODAY, NOW, "E"), "ORDER_EMPTY");
        assertRule(() -> c.placeOrder(order(SIGNED, c, "0"), TODAY, NOW, "E"), "QUANTITY_NOT_POSITIVE");
        UUID item = c.getItems().get(0).getId();
        OrderDraft twice = new OrderDraft(
                SIGNED,
                null,
                null,
                null,
                List.of(new OrderDraft.Line(item, BigDecimal.ONE), new OrderDraft.Line(item, BigDecimal.ONE)));
        assertRule(() -> c.placeOrder(twice, TODAY, NOW, "E"), "DUPLICATE_ITEM");
        OrderDraft unknown = new OrderDraft(
                SIGNED, null, null, null, List.of(new OrderDraft.Line(UUID.randomUUID(), BigDecimal.ONE)));
        assertRule(() -> c.placeOrder(unknown, TODAY, NOW, "E"), "ITEM_NOT_FOUND");
    }

    @Test
    void removingAnOrderGivesTheBalanceBack() {
        Contract c = withItems();
        ContractOrder o = c.placeOrder(order(SIGNED, c, "10"), TODAY, NOW, "E");
        c.removeOrder(o.getId());
        assertThat(c.balance().ordered()).isEqualByComparingTo("0");
        assertRule(() -> c.removeOrder(o.getId()), "ORDER_NOT_FOUND");
    }

    @Test
    void itemsWithOrdersAreProtected() {
        Contract c = withItems();
        c.placeOrder(order(SIGNED, c, "800"), TODAY, NOW, "E");
        ContractItem banner = c.getItems().get(0);
        ContractItem adesivo = c.getItems().get(1);

        assertRule(() -> c.replaceItems(List.of(itemDraft(adesivo, "4000"))), "ITEM_HAS_ORDERS");
        assertRule(
                () -> c.replaceItems(List.of(itemDraft(banner, "799"), itemDraft(adesivo, "4000"))),
                "QUANTITY_BELOW_ORDERED");

        // adesivo sem pedido pode sair; item novo entra no fim
        c.replaceItems(List.of(itemDraft(banner, "2500"), item("Faixa", "10", "50")));
        assertThat(c.getItems()).extracting(ContractItem::getDescription).containsExactly("Banner", "Faixa");
        assertThat(c.balance().items().get(0).remaining()).isEqualByComparingTo("1700");
    }

    @Test
    void contractValueCannotDropBelowWhatWasOrdered() {
        Contract c = withItems();
        c.placeOrder(order(SIGNED, c, "800"), TODAY, NOW, "E");
        assertRule(() -> c.update(draft("79999.99"), WON, TODAY), "VALUE_BELOW_ORDERED");
        ContractDraft late = new ContractDraft(
                ContractKind.CONTRATO, null, SIGNED.plusDays(1), null, new BigDecimal("300000"), null);
        assertRule(() -> c.update(late, WON, TODAY), "DATE_ORDER");
    }

    @Test
    void closesAfterTheLastOrderAndThenRejectsChanges() {
        Contract c = withItems();
        c.placeOrder(order(LocalDate.of(2026, 9, 1), c, "10"), TODAY, NOW, "E");
        assertRule(() -> c.close(null, TODAY), "DATE_REQUIRED");
        assertRule(() -> c.close(LocalDate.of(2026, 8, 31), TODAY), "DATE_ORDER");
        assertRule(() -> c.close(TODAY.plusDays(1), TODAY), "DATE_IN_FUTURE");

        c.close(LocalDate.of(2026, 9, 30), TODAY);
        assertThat(c.isClosed()).isTrue();
        assertRule(() -> c.placeOrder(order(TODAY, c, "1"), TODAY, NOW, "E"), "CONTRACT_CLOSED");
        assertRule(() -> c.close(TODAY, TODAY), "CONTRACT_CLOSED");
    }

    @Test
    void lineTotalsRoundToCents() {
        Contract c = Contract.sign(UUID.randomUUID(), draft("1000.00"), WON, TODAY);
        c.replaceItems(List.of(item("Caneta", "3", "0.3333")));
        ContractOrder o = c.placeOrder(order(SIGNED, c, "3"), TODAY, NOW, "E");
        assertThat(o.getValue()).isEqualByComparingTo("1.00");
        assertThat(c.itemsTotal()).isEqualByComparingTo("1.00");
    }

    @Test
    void percentOrderedRoundsDown() {
        ContractBalance almost = new ContractBalance(
                new BigDecimal("1000.00"), new BigDecimal("999.99"), new BigDecimal("0.01"), List.of());
        assertThat(almost.percentOrdered()).isEqualTo(99);
        ContractBalance free = new ContractBalance(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        assertThat(free.percentOrdered()).isZero();
    }

    private static ItemDraft itemDraft(ContractItem existing, String quantity) {
        return new ItemDraft(
                existing.getId(),
                existing.getLot(),
                existing.getDescription(),
                existing.getUnit(),
                new BigDecimal(quantity),
                existing.getUnitPrice());
    }
}
