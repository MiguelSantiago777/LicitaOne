package br.com.shema.crm.boards.domain;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Quadro kanban com colunas definidas pela equipe (nada de colunas fixas no código). A versão protege a estrutura:
 * duas pessoas renomeando ou reordenando colunas ao mesmo tempo recebem aviso. Os cards ficam fora do agregado, para
 * arrastar um card não disputar a versão do quadro.
 */
@Entity
@Table(name = "boards")
public class Board extends AuditableEntity {

    public static final int MAX_COLUMNS = 20;

    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private int position;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "board_id", nullable = false)
    @OrderBy("position")
    private List<BoardColumn> columns = new ArrayList<>();

    protected Board() {}

    public Board(String name, int position) {
        this.id = UUID.randomUUID();
        this.position = position;
        rename(name);
    }

    public void rename(String name) {
        this.name = text(name, "o nome do quadro", 80);
    }

    public BoardColumn addColumn(String name) {
        if (columns.size() >= MAX_COLUMNS) {
            throw new DomainRuleViolation("TOO_MANY_COLUMNS", "Um quadro tem no máximo " + MAX_COLUMNS + " colunas.");
        }
        BoardColumn column = new BoardColumn(text(name, "o nome da coluna", 60), columns.size());
        columns.add(column);
        return column;
    }

    public void renameColumn(UUID columnId, String name) {
        column(columnId).rename(text(name, "o nome da coluna", 60));
    }

    /** Leva a coluna para a posição (começa em 0) e renumera as outras. */
    public void moveColumn(UUID columnId, int position) {
        BoardColumn column = column(columnId);
        columns.remove(column);
        columns.add(Math.clamp(position, 0, columns.size()), column);
        renumber();
    }

    /** Só sai coluna vazia: os cards não somem sem alguém decidir para onde vão. */
    public void removeColumn(UUID columnId, long cardsInColumn) {
        BoardColumn column = column(columnId);
        if (cardsInColumn > 0) {
            throw new DomainRuleViolation(
                    "COLUMN_NOT_EMPTY",
                    "A coluna \"" + column.getName() + "\" tem " + cardsInColumn
                            + (cardsInColumn == 1 ? " card" : " cards") + ". Mova ou apague antes de excluir.");
        }
        columns.remove(column);
        renumber();
    }

    public BoardColumn column(UUID columnId) {
        return columns.stream()
                .filter(c -> c.getId().equals(columnId))
                .findFirst()
                .orElseThrow(() -> new DomainRuleViolation("COLUMN_NOT_FOUND", "Coluna não encontrada neste quadro."));
    }

    private void renumber() {
        for (int i = 0; i < columns.size(); i++) {
            columns.get(i).moveTo(i);
        }
    }

    static String text(String value, String what, int max) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleViolation("REQUIRED", "Informe " + what + ".");
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        if (trimmed.length() > max) {
            throw new DomainRuleViolation("TOO_LONG", "Use no máximo " + max + " caracteres para " + what + ".");
        }
        return trimmed;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getPosition() {
        return position;
    }

    public List<BoardColumn> getColumns() {
        return List.copyOf(columns);
    }
}
