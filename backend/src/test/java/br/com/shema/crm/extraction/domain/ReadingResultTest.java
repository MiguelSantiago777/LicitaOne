package br.com.shema.crm.extraction.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.shema.crm.extraction.domain.RawReading.RawField;
import br.com.shema.crm.extraction.domain.RawReading.RawItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReadingResultTest {

    private static final String SHEMA = "Shema Suprimentos e Serviços";

    private static RawField f(String value, int page) {
        return new RawField(value, page);
    }

    private static RawField none() {
        return new RawField("", 0);
    }

    private static RawReading ata(List<RawItem> items, RawField total, List<String> warnings) {
        return new RawReading(
                "ata",
                f("012/2026", 1),
                f("2026-05-16", 1),
                none(),
                f("2027-05-15", 2),
                f("SIM", 3),
                total,
                f("Prefeitura Municipal de Magé", 1),
                f("SHEMA SUPRIMENTOS E SERVIÇOS LTDA", 1),
                items,
                warnings);
    }

    @Test
    void convertsDatesNumbersAndPagesOfAWellReadAta() {
        ReadingResult r = ReadingResult.from(
                ata(
                        List.of(
                                new RawItem("1", "Banner em lona 440g", "M²", "2000", "45.50", 4),
                                new RawItem("", "Adesivo perfurado", "", "1500.5", "12.3", 5)),
                        f("109456.15", 2),
                        List.of(" ", "A tabela da página 5 está um pouco borrada.")),
                SHEMA);

        assertThat(r.documentType()).isEqualTo("ATA");
        assertThat(r.number()).isEqualTo(new ReadingResult.Value<>("012/2026", 1));
        assertThat(r.signedAt().value()).isEqualTo(LocalDate.of(2026, 5, 16));
        assertThat(r.validFrom()).isEqualTo(new ReadingResult.Value<>(null, null));
        assertThat(r.validUntil()).isEqualTo(new ReadingResult.Value<>(LocalDate.of(2027, 5, 15), 2));
        assertThat(r.allowsAdhesion()).isEqualTo(new ReadingResult.Value<>(true, 3));
        assertThat(r.totalValue().value()).isEqualByComparingTo("109456.15");
        assertThat(r.items()).hasSize(2);
        assertThat(r.items().getFirst())
                .isEqualTo(new ReadingResult.Item(
                        "1", "Banner em lona 440g", "M²", new BigDecimal("2000.000"), new BigDecimal("45.5000"), 4));
        assertThat(r.items().get(1).lot()).isNull();
        assertThat(r.items().get(1).unit()).isNull();
        assertThat(r.warnings()).containsExactly("A tabela da página 5 está um pouco borrada.");
    }

    @Test
    void acceptsBrazilianFormatsCopiedFromTheDocument() {
        assertThat(ReadingResult.number("R$ 1.234,56", 2)).isEqualByComparingTo("1234.56");
        assertThat(ReadingResult.number("2.000", 3)).isEqualByComparingTo("2.000");
        assertThat(ReadingResult.number("0,0125", 4)).isEqualByComparingTo("0.0125");
        assertThat(ReadingResult.number("12.345.678,9", 2)).isEqualByComparingTo("12345678.90");
        assertThat(ReadingResult.number("dois mil", 2)).isNull();
        assertThat(ReadingResult.number(" ", 2)).isNull();

        RawReading raw = new RawReading(
                "Contrato",
                f("CT 07/2026", 1),
                f("16/05/2026", 1),
                f("16/05/2026", 1),
                f("15/05/2027", 1),
                none(),
                f("R$ 300.000,00", 1),
                none(),
                none(),
                List.of(),
                null);
        ReadingResult r = ReadingResult.from(raw, SHEMA);
        assertThat(r.documentType()).isEqualTo("CONTRATO");
        assertThat(r.signedAt().value()).isEqualTo(LocalDate.of(2026, 5, 16));
        assertThat(r.validUntil().value()).isEqualTo(LocalDate.of(2027, 5, 15));
        assertThat(r.totalValue().value()).isEqualByComparingTo("300000.00");
        assertThat(r.allowsAdhesion().value()).isNull();
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    void unreadableValuesBecomeEmptyWithAWarning() {
        RawReading raw = new RawReading(
                "edital",
                f("  ", 1),
                f("16 de maio", 1),
                none(),
                f("2026-13-40", 2),
                f("talvez", 3),
                f("trezentos mil", 2),
                none(),
                none(),
                List.of(new RawItem("1", "Banner", "UN", "0", "abc", 4), new RawItem("2", " ", "UN", "10", "1.00", 4)),
                List.of());
        ReadingResult r = ReadingResult.from(raw, SHEMA);

        assertThat(r.documentType()).isEqualTo("OUTRO");
        assertThat(r.number().value()).isNull();
        assertThat(r.signedAt().value()).isNull();
        assertThat(r.validUntil().value()).isNull();
        assertThat(r.allowsAdhesion().value()).isNull();
        assertThat(r.totalValue().value()).isNull();
        assertThat(r.items()).hasSize(1);
        assertThat(r.items().getFirst().quantity()).isNull();
        assertThat(r.items().getFirst().unitPrice()).isNull();
        assertThat(r.warnings())
                .containsExactly(
                        "Não deu para ler a data de assinatura (\"16 de maio\").",
                        "Não deu para ler o fim da vigência (\"2026-13-40\").",
                        "Não deu para ler o valor total (\"trezentos mil\").",
                        "Não deu para ler a quantidade do item 1.",
                        "Não deu para ler o preço unitário do item 1.",
                        "O item 2 veio sem descrição e foi deixado de fora.");
    }

    @Test
    void warnsWhenTheReadingDoesNotAddUp() {
        RawReading raw = new RawReading(
                "ATA",
                f("012/2026", 1),
                f("2026-05-16", 1),
                none(),
                f("2025-05-15", 2),
                f("NAO", 3),
                f("1000.00", 2),
                none(),
                f("Gráfica Concorrente Ltda", 1),
                List.of(new RawItem("1", "Banner", "UN", "10", "50.00", 4)),
                List.of());
        ReadingResult r = ReadingResult.from(raw, SHEMA);

        assertThat(r.allowsAdhesion().value()).isFalse();
        assertThat(r.warnings())
                .containsExactly(
                        "O fim da vigência lido (15/05/2025) é anterior ao início. Confira as datas.",
                        "O fornecedor lido é \"Gráfica Concorrente Ltda\". Confira se o documento é da " + SHEMA + ".",
                        "A soma dos itens (R$ 500,00) não bate com o valor total lido (R$ 1.000,00)."
                                + " Pode faltar item ou ter preço lido errado.");
    }

    @Test
    void roundingDifferencesInTheTotalAreAccepted() {
        ReadingResult r = ReadingResult.from(
                ata(List.of(new RawItem("1", "Banner", "UN", "3", "33.333", 4)), f("100.00", 2), List.of()), SHEMA);
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    void missingPartsOfTheReadingDoNotBreakIt() {
        RawReading raw = new RawReading(null, null, null, null, null, null, null, null, null, null, null);
        ReadingResult r = ReadingResult.from(raw, SHEMA);
        assertThat(r.documentType()).isEqualTo("OUTRO");
        assertThat(r.number().value()).isNull();
        assertThat(r.items()).isEmpty();
        assertThat(r.warnings()).isEmpty();
    }
}
