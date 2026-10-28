package br.com.shema.crm.imports.application;

import br.com.shema.crm.contracts.ContractItemsImport.RowOutcome;
import br.com.shema.crm.imports.domain.ItemSheet.ParsedRow;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/** Relatório final da importação, linha a linha, em CSV que abre direto no Excel em português (";" e BOM). */
final class ImportReport {

    private static final String[] HEADER = {
        "Linha", "Situação", "Lote", "Descrição", "Quantidade", "Valor unitário", "Pedido lançado", "Mensagem"
    };

    /** Marca de UTF-8 no início: sem ela o Excel abre o CSV como Windows-1252 e estraga os acentos. */
    private static final char BOM = (char) 0xFEFF;

    private ImportReport() {}

    static byte[] csv(List<ParsedRow> rows, Map<Integer, RowOutcome> outcomes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // O BOM vai antes de criar o CSVPrinter, que já escreve o cabeçalho no construtor
        out.writeBytes(String.valueOf(BOM).getBytes(StandardCharsets.UTF_8));
        try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                CSVPrinter csv = new CSVPrinter(
                        writer,
                        CSVFormat.DEFAULT
                                .builder()
                                .setDelimiter(';')
                                .setHeader(HEADER)
                                .get())) {
            for (ParsedRow row : rows) {
                RowOutcome outcome = outcomes.get(row.rowNumber());
                String status = outcome == null ? "Erro" : label(outcome.status());
                String message = outcome == null ? String.join(" ", row.errors()) : outcome.message();
                csv.printRecord(
                        row.rowNumber(),
                        status,
                        row.lot(),
                        row.description(),
                        br(row.quantity()),
                        br(row.unitPrice()),
                        outcome == null ? null : br(outcome.newOrderQuantity()),
                        message);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static String label(br.com.shema.crm.contracts.ContractItemsImport.RowStatus status) {
        return switch (status) {
            case CREATED -> "Item criado";
            case UPDATED -> "Item alterado";
            case UNCHANGED -> "Sem mudança";
            case WARNING -> "Aviso";
        };
    }

    /** Número com vírgula decimal, sem zeros sobrando ("4,1", "2000"). */
    private static String br(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
