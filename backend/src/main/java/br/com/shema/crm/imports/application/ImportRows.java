package br.com.shema.crm.imports.application;

import br.com.shema.crm.contracts.ContractItemsImport.ImportedItem;
import br.com.shema.crm.imports.domain.ItemSheet.ParsedRow;
import java.util.List;

/** Converte as linhas válidas da planilha no formato da API pública de contratos. */
final class ImportRows {

    private ImportRows() {}

    static List<ImportedItem> valid(List<ParsedRow> rows) {
        return rows.stream()
                .filter(ParsedRow::valid)
                .map(r -> new ImportedItem(
                        r.rowNumber(),
                        r.lot(),
                        r.description(),
                        r.unit(),
                        r.quantity(),
                        r.unitPrice(),
                        r.orderedQuantity()))
                .toList();
    }
}
