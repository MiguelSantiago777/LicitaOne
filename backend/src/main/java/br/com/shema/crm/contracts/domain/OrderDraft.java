package br.com.shema.crm.contracts.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Pedido a registrar. Contrato com itens: informe as linhas (o valor é calculado). Contrato sem itens: informe só
 * o valor.
 *
 * @param sourceDocumentId documento de onde o pedido foi lido (ofício, AF, empenho), se houver
 */
public record OrderDraft(
        LocalDate orderedAt,
        String reference,
        String notes,
        BigDecimal value,
        List<Line> lines,
        UUID sourceDocumentId) {

    public record Line(UUID itemId, BigDecimal quantity) {}

    public OrderDraft {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public OrderDraft(LocalDate orderedAt, String reference, String notes, BigDecimal value, List<Line> lines) {
        this(orderedAt, reference, notes, value, lines, null);
    }
}
