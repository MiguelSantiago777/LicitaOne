package br.com.shema.crm.boards.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardTest {

    private static void assertRule(Runnable action, String rule) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DomainRuleViolation.class)
                .extracting(e -> ((DomainRuleViolation) e).rule())
                .isEqualTo(rule);
    }

    @Test
    void teamDefinesTheColumns() {
        Board board = new Board("  Organização   mensal ", 0);
        assertThat(board.getName()).isEqualTo("Organização mensal");
        assertThat(board.getColumns()).isEmpty();

        BoardColumn semana1 = board.addColumn("Semana 1");
        BoardColumn semana2 = board.addColumn("Semana 2");
        BoardColumn feito = board.addColumn("Feito");
        board.renameColumn(semana2.getId(), "Semana 2 (feriado)");
        board.moveColumn(feito.getId(), 0);

        assertThat(board.getColumns())
                .extracting(BoardColumn::getName)
                .containsExactly("Feito", "Semana 1", "Semana 2 (feriado)");
        assertThat(board.getColumns()).extracting(BoardColumn::getPosition).containsExactly(0, 1, 2);

        board.removeColumn(semana1.getId(), 0);
        assertThat(board.getColumns()).extracting(BoardColumn::getPosition).containsExactly(0, 1);
    }

    @Test
    void columnWithCardsCannotBeRemoved() {
        Board board = new Board("Licitações vencidas", 1);
        BoardColumn col = board.addColumn("Aguardando contrato");
        assertRule(() -> board.removeColumn(col.getId(), 2), "COLUMN_NOT_EMPTY");
        assertRule(() -> board.removeColumn(UUID.randomUUID(), 0), "COLUMN_NOT_FOUND");
    }

    @Test
    void namesAreRequiredAndLimited() {
        assertRule(() -> new Board(" ", 0), "REQUIRED");
        Board board = new Board("Quadro", 0);
        assertRule(() -> board.addColumn("x".repeat(61)), "TOO_LONG");
        for (int i = 0; i < Board.MAX_COLUMNS; i++) {
            board.addColumn("C" + i);
        }
        assertRule(() -> board.addColumn("Mais uma"), "TOO_MANY_COLUMNS");
    }

    @Test
    void cardIsOverdueOnlyAfterTheDueDate() {
        LocalDate today = LocalDate.of(2026, 10, 28);
        BoardCard card = new BoardCard(
                UUID.randomUUID(), UUID.randomUUID(), 0, new CardDraft("Ligar para Magé", null, null, today, null));
        assertThat(card.isOverdue(today)).isFalse();
        assertThat(card.isOverdue(today.plusDays(1))).isTrue();
        assertRule(() -> card.update(new CardDraft("", null, null, null, null)), "REQUIRED");
    }
}
