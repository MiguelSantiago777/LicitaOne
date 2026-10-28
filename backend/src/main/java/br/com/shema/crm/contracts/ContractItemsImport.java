package br.com.shema.crm.contracts;

import br.com.shema.crm.shared.security.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * API pública para importar itens e pedidos de um contrato a partir da planilha que a equipe já usa (aba de cada
 * órgão). Reimportar a mesma planilha não duplica nada: os itens são casados pelos já existentes e o pedido criado é
 * só a diferença entre o "pedido" da planilha e o que já está registrado.
 */
public interface ContractItemsImport {

    Optional<ContractSummary> find(UUID contractId);

    /** O que a importação faria, sem gravar nada. */
    List<RowOutcome> preview(UUID contractId, List<ImportedItem> rows);

    /** Grava itens e o pedido da diferença, numa transação só. */
    ImportOutcome apply(UUID contractId, List<ImportedItem> rows, LocalDate orderDate, CurrentUser by);

    /** @param label "Prefeitura de Magé · Contrato 12/2026", para telas e relatórios */
    record ContractSummary(UUID id, UUID tenderId, UUID ownerId, String label, boolean closed) {}

    /**
     * Uma linha da planilha, já convertida.
     *
     * @param orderedQuantity total pedido até hoje segundo a planilha (não o pedido novo), ou null se não houver
     */
    record ImportedItem(
            int rowNumber,
            String lot,
            String description,
            String unit,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal orderedQuantity) {}

    enum RowStatus {
        CREATED,
        UPDATED,
        UNCHANGED,
        WARNING
    }

    record RowOutcome(int rowNumber, RowStatus status, String message, BigDecimal newOrderQuantity) {}

    record ImportOutcome(List<RowOutcome> rows, UUID orderId, BigDecimal orderValue) {}
}
