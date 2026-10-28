package br.com.shema.crm.imports.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.shema.crm.imports.SefazSheet;
import br.com.shema.crm.imports.infrastructure.SpreadsheetReader;
import br.com.shema.crm.shared.domain.DomainRuleViolation;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ItemSheetTest {

    private final SpreadsheetReader reader = new SpreadsheetReader();

    private SheetData sheet(String name, byte[] xlsx) {
        return reader.read("planilha.xlsx", xlsx).stream()
                .filter(s -> s.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void readsTheClientLayoutAndFindsTheHeaderOnRowFive() {
        List<SheetData> sheets = reader.read("PLANILHA.xlsx", SefazSheet.xlsx(false));
        assertThat(sheets).extracting(SheetData::name).containsExactly("GERAL", "SEFAZ - CONTRATO", "SEGOV  UNIFORMES");

        SheetData sefaz = sheets.get(1);
        ItemSheet.Header header = ItemSheet.detectHeader(sefaz.rows()).orElseThrow();
        assertThat(header.rowNumber()).isEqualTo(5);
        // "PEDIDO V.U." não confunde com "V.U."; "ITENS" (número do item) não é descrição
        assertThat(header.mapping())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        ItemField.DESCRIPTION, 2,
                        ItemField.QUANTITY, 3,
                        ItemField.UNIT_PRICE, 4,
                        ItemField.ORDERED_QUANTITY, 7));
    }

    @Test
    void parsesItemsIgnoringTotalsAndRoundingFloatingNoise() {
        SheetData sefaz = sheet("SEFAZ - CONTRATO", SefazSheet.xlsx(true));
        ItemSheet.Header header = ItemSheet.detectHeader(sefaz.rows()).orElseThrow();
        List<ItemSheet.ParsedRow> rows = ItemSheet.parse(sefaz.rows(), header.rowNumber(), header.mapping());

        assertThat(rows).extracting(ItemSheet.ParsedRow::rowNumber).containsExactly(6, 7, 8, 9, 10, 11);
        ItemSheet.ParsedRow caneta = rows.get(4);
        assertThat(caneta.description()).isEqualTo("CANETA ESCOLAR PERSONALIZADA AZUL");
        assertThat(caneta.unitPrice()).isEqualByComparingTo("4.1");
        assertThat(caneta.orderedQuantity()).isEqualByComparingTo("1200");
        assertThat(caneta.valid()).isTrue();

        ItemSheet.ParsedRow bad = rows.get(5);
        assertThat(bad.valid()).isFalse();
        assertThat(bad.errors()).containsExactly("Quantidade não é um número: \"setenta e dois\".");
    }

    @Test
    void mergedLotAppliesToEveryRowItCovers() {
        SheetData segov = sheet("SEGOV  UNIFORMES", SefazSheet.xlsx(false));
        ItemSheet.Header header = ItemSheet.detectHeader(segov.rows()).orElseThrow();
        assertThat(header.mapping()).containsEntry(ItemField.LOT, 1);
        List<ItemSheet.ParsedRow> rows = ItemSheet.parse(segov.rows(), header.rowNumber(), header.mapping());
        assertThat(rows).extracting(ItemSheet.ParsedRow::lot).containsExactly("1", "1");
    }

    @Test
    void readsCsvWithSemicolonCommaDecimalsAndWindowsEncoding() {
        String csv = "Descrição;Quantidade;Valor unitário;Qtd pedida\n"
                + "Banner lona;1.200;R$ 75,50;100\n"
                + ";;;\n"
                + "Faixa;dez;20;\n";
        SheetData data = reader.read("itens.csv", csv.getBytes(java.nio.charset.Charset.forName("windows-1252")))
                .getFirst();
        ItemSheet.Header header = ItemSheet.detectHeader(data.rows()).orElseThrow();
        assertThat(header.rowNumber()).isEqualTo(1);
        List<ItemSheet.ParsedRow> rows = ItemSheet.parse(data.rows(), header.rowNumber(), header.mapping());
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().description()).isEqualTo("Banner lona");
        assertThat(rows.getFirst().unitPrice()).isEqualByComparingTo("75.50");
        assertThat(rows.get(1).valid()).isFalse();

        String withBom = (char) 0xFEFF + "Descrição,Quantidade\nA,1\n";
        SheetData utf8 =
                reader.read("x.csv", withBom.getBytes(StandardCharsets.UTF_8)).getFirst();
        assertThat(utf8.rows().getFirst().cell(0)).isEqualTo("Descrição");
    }

    @Test
    void parsesBrazilianAndPlainNumbers() {
        assertThat(ItemSheet.parseNumber("1.234,56")).isEqualByComparingTo("1234.56");
        assertThat(ItemSheet.parseNumber("R$ 2.573.225,29")).isEqualByComparingTo("2573225.29");
        assertThat(ItemSheet.parseNumber("287201.54")).isEqualByComparingTo("287201.54");
        assertThat(ItemSheet.parseNumber("1.234.567")).isEqualByComparingTo("1234567");
    }

    @Test
    void reportsMissingRequiredColumnsAndRejectsOtherFiles() {
        assertThat(ItemSheet.missingRequired(Map.of(ItemField.DESCRIPTION, 0)))
                .containsExactly(ItemField.QUANTITY, ItemField.UNIT_PRICE);
        assertThatThrownBy(() -> reader.read("contrato.pdf", new byte[] {1, 2, 3}))
                .isInstanceOf(DomainRuleViolation.class)
                .hasMessageContaining(".xlsx ou .csv");
        assertThatThrownBy(() -> reader.read("falsa.xlsx", "PK nada".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DomainRuleViolation.class)
                .hasMessageContaining("Não foi possível ler");
    }
}
