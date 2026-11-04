package br.com.shema.crm.extraction.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.shema.crm.extraction.domain.RawOrder.RawOrderLine;
import br.com.shema.crm.extraction.domain.RawReading.RawField;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderReadingResultTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);
    private static final UUID BANNER = UUID.randomUUID();
    private static final UUID ADESIVO = UUID.randomUUID();

    private static final OrderReadingResult.Contract CONTRACT = new OrderReadingResult.Contract(
            "Prefeitura Municipal de Magé",
            "12/2026",
            LocalDate.of(2026, 6, 1),
            List.of(
                    new OrderReadingResult.Item(
                            BANNER, "Banner em lona", new BigDecimal("45.00"), new BigDecimal("800")),
                    new OrderReadingResult.Item(
                            ADESIVO, "Adesivo perfurado", new BigDecimal("30.00"), new BigDecimal("1000"))));

    private static RawField f(String value, int page) {
        return new RawField(value, page);
    }

    private static RawField none() {
        return new RawField("", 0);
    }

    private static RawOrderLine line(String item, String description, String quantity, String price) {
        return new RawOrderLine(item, description, "M²", quantity, price, 1, description);
    }

    private static RawOrder order(RawField date, RawField agency, RawField cited, List<RawOrderLine> lines) {
        return new RawOrder(
                "autorizacao_fornecimento", date, f("AF 77/2026", 1), agency, cited, none(), lines, List.of());
    }

    @Test
    void matchesLinesToContractItemsAndKeepsTheContractPrice() {
        OrderReadingResult r = OrderReadingResult.from(
                order(
                        f("2026-10-20", 1),
                        f("PREFEITURA MUNICIPAL DE MAGÉ", 1),
                        f("Contrato nº 012/2026", 1),
                        List.of(line("1", "Banner lona 440g", "200", "45.00"), line("2", "Adesivo", "100,5", ""))),
                CONTRACT,
                TODAY);

        assertThat(r.documentType()).isEqualTo("AUTORIZACAO_FORNECIMENTO");
        assertThat(r.orderedAt().value()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(r.reference().value()).isEqualTo("AF 77/2026");
        assertThat(r.warnings()).isEmpty();
        assertThat(r.lines()).hasSize(2);
        OrderReadingResult.Line banner = r.lines().getFirst();
        assertThat(banner.itemId()).isEqualTo(BANNER);
        assertThat(banner.itemNumber()).isEqualTo(1);
        assertThat(banner.quantity()).isEqualByComparingTo("200");
        assertThat(banner.contractUnitPrice()).isEqualByComparingTo("45.00");
        assertThat(banner.remaining()).isEqualByComparingTo("800");
        assertThat(banner.issues()).isEmpty();
        assertThat(r.lines().get(1).quantity()).isEqualByComparingTo("100.5");
    }

    @Test
    void pointsOutLinesWithoutItemAboveTheBalanceAndWithAnotherPrice() {
        OrderReadingResult r = OrderReadingResult.from(
                order(
                        none(),
                        none(),
                        none(),
                        List.of(
                                line("1", "Banner", "900", "50.00"),
                                line("", "Toner 85A", "3", "120.00"),
                                line("7", "Item que não existe", "1", ""))),
                CONTRACT,
                TODAY);

        assertThat(r.lines()).hasSize(3);
        assertThat(r.lines().getFirst().issues())
                .anySatisfy(i -> assertThat(i).contains("saldo é de 800"))
                .anySatisfy(i -> assertThat(i).contains("usa o preço do contrato"));
        assertThat(r.lines().get(1).itemId()).isNull();
        assertThat(r.lines().get(1).issues()).first().asString().contains("Não corresponde");
        assertThat(r.lines().get(2).itemId()).as("número fora da lista").isNull();
    }

    @Test
    void addsUpTheSameItemOrderedInTwoLines() {
        OrderReadingResult r = OrderReadingResult.from(
                order(none(), none(), none(), List.of(line("2", "Adesivo", "100", ""), line("2", "Adesivo", "50", ""))),
                CONTRACT,
                TODAY);

        assertThat(r.lines()).hasSize(1);
        assertThat(r.lines().getFirst().quantity()).isEqualByComparingTo("150");
        assertThat(r.warnings()).anySatisfy(w -> assertThat(w).contains("mais de uma linha"));
    }

    @Test
    void warnsWhenTheOrderLooksLikeAnotherContract() {
        OrderReadingResult r = OrderReadingResult.from(
                order(
                        f("2026-05-01", 1),
                        f("Prefeitura de Itaboraí", 1),
                        f("Contrato 45/2025", 1),
                        List.of(line("1", "Banner", "10", ""))),
                CONTRACT,
                TODAY);

        assertThat(r.warnings())
                .anySatisfy(w -> assertThat(w).contains("anterior à assinatura"))
                .anySatisfy(w -> assertThat(w).contains("Itaboraí"))
                .anySatisfy(w -> assertThat(w).contains("45/2025"));
    }

    @Test
    void warnsAboutAFutureDateAndAnOrderWithoutItems() {
        OrderReadingResult r =
                OrderReadingResult.from(order(f("2026-12-01", 1), none(), none(), List.of()), CONTRACT, TODAY);

        assertThat(r.lines()).isEmpty();
        assertThat(r.warnings())
                .anySatisfy(w -> assertThat(w).contains("no futuro"))
                .anySatisfy(w -> assertThat(w).contains("não encontrou itens"));
    }

    @Test
    void comparesAgenciesBySignificantWords() {
        assertThat(OrderReadingResult.sameAgency("Município de Magé - Secretaria de Educação", "Prefeitura de Magé"))
                .isTrue();
        assertThat(OrderReadingResult.sameAgency("Prefeitura Municipal", "Prefeitura de Magé"))
                .as("sem palavra significativa, não dá para dizer")
                .isTrue();
        assertThat(OrderReadingResult.sameAgency("Prefeitura de Maricá", "Prefeitura de Magé"))
                .isFalse();
    }
}
