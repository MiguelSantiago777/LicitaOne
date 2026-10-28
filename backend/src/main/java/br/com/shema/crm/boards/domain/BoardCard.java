package br.com.shema.crm.boards.domain;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.OptimisticLock;

/**
 * Card livre de um quadro: título, observações, responsável e prazo, opcionalmente ligado a uma licitação. A versão
 * protege a edição do conteúdo; mudar de coluna ou de posição não mexe nela.
 */
@Entity
@Table(name = "board_cards")
public class BoardCard extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "board_id", nullable = false)
    private UUID boardId;

    @OptimisticLock(excluded = true)
    @Column(name = "column_id", nullable = false)
    private UUID columnId;

    @OptimisticLock(excluded = true)
    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String notes;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "tender_id")
    private UUID tenderId;

    protected BoardCard() {}

    public BoardCard(UUID boardId, UUID columnId, int position, CardDraft draft) {
        this.id = UUID.randomUUID();
        this.boardId = boardId;
        this.columnId = columnId;
        this.position = position;
        update(draft);
    }

    public void update(CardDraft d) {
        this.title = Board.text(d.title(), "o título do card", 200);
        if (d.notes() != null && d.notes().length() > 2000) {
            throw new DomainRuleViolation("TOO_LONG", "Use no máximo 2000 caracteres nas observações.");
        }
        this.notes = d.notes() == null || d.notes().isBlank() ? null : d.notes().trim();
        this.ownerId = d.ownerId();
        this.dueDate = d.dueDate();
        this.tenderId = d.tenderId();
    }

    /** Posição dentro da coluna; quem renumera os vizinhos é o serviço. */
    public void placeAt(UUID columnId, int position) {
        this.columnId = columnId;
        this.position = position;
    }

    public boolean isOverdue(LocalDate today) {
        return dueDate != null && dueDate.isBefore(today);
    }

    public UUID getId() {
        return id;
    }

    public UUID getBoardId() {
        return boardId;
    }

    public UUID getColumnId() {
        return columnId;
    }

    public int getPosition() {
        return position;
    }

    public String getTitle() {
        return title;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public UUID getTenderId() {
        return tenderId;
    }
}
