package br.com.shema.crm.boards.application;

import br.com.shema.crm.boards.domain.Board;
import br.com.shema.crm.boards.domain.BoardCard;
import br.com.shema.crm.boards.domain.BoardColumn;
import br.com.shema.crm.boards.domain.CardDraft;
import br.com.shema.crm.boards.infrastructure.BoardCardRepository;
import br.com.shema.crm.boards.infrastructure.BoardRepository;
import br.com.shema.crm.identity.UserDirectory;
import br.com.shema.crm.identity.UserSummary;
import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.security.CurrentUser;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import br.com.shema.crm.tenders.TenderLifecycle;
import br.com.shema.crm.tenders.TenderRef;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso dos quadros. Quadro e colunas: só ADMIN e COMERCIAL alteram, com a versão do quadro. Cards: a versão
 * protege o conteúdo; mover trava o quadro só durante a renumeração, para dois arrastes simultâneos não embaralharem
 * as posições.
 */
@Service
@Transactional
public class BoardService {

    public record BoardSummary(UUID id, String name, long cards) {}

    private final BoardRepository boards;
    private final BoardCardRepository cards;
    private final UserDirectory users;
    private final TenderLifecycle tenders;
    private final EntityManager entityManager;
    private final Clock clock;

    public BoardService(
            BoardRepository boards,
            BoardCardRepository cards,
            UserDirectory users,
            TenderLifecycle tenders,
            EntityManager entityManager,
            Clock clock) {
        this.boards = boards;
        this.cards = cards;
        this.users = users;
        this.tenders = tenders;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<BoardSummary> list() {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : cards.countByBoard()) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return boards.findAllByOrderByPositionAscNameAsc().stream()
                .map(b -> new BoardSummary(b.getId(), b.getName(), counts.getOrDefault(b.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public BoardView view(UUID boardId) {
        return view(load(boardId));
    }

    public BoardView create(String name) {
        requireEditor();
        Board board = new Board(name, boards.maxPosition() + 1);
        boards.saveAndFlush(board);
        return view(board);
    }

    public BoardView rename(UUID boardId, long version, String name) {
        Board board = editable(boardId, version);
        board.rename(name);
        boards.flush();
        return view(board);
    }

    public void delete(UUID boardId, long version) {
        Board board = editable(boardId, version);
        long count = cards.countByBoardId(boardId);
        if (count > 0) {
            throw new DomainRuleViolation(
                    "BOARD_NOT_EMPTY", "O quadro tem " + count + (count == 1 ? " card" : " cards") + ". Apague antes.");
        }
        boards.delete(board);
    }

    public BoardView addColumn(UUID boardId, long version, String name) {
        Board board = structureChange(boardId, version);
        board.addColumn(name);
        boards.flush();
        return view(board);
    }

    public BoardView renameColumn(UUID boardId, long version, UUID columnId, String name) {
        Board board = structureChange(boardId, version);
        board.renameColumn(columnId, name);
        boards.flush();
        return view(board);
    }

    public BoardView moveColumn(UUID boardId, long version, UUID columnId, int position) {
        Board board = structureChange(boardId, version);
        board.moveColumn(columnId, position);
        boards.flush();
        return view(board);
    }

    public BoardView removeColumn(UUID boardId, long version, UUID columnId) {
        Board board = structureChange(boardId, version);
        board.removeColumn(columnId, cards.countByColumnId(columnId));
        boards.flush();
        return view(board);
    }

    /** Card novo entra no fim da coluna. */
    public BoardView addCard(UUID boardId, UUID columnId, CardDraft draft) {
        requireEditor();
        Board board = load(boardId);
        board.column(columnId);
        validateLinks(draft);
        int position = (int) cards.countByColumnId(columnId);
        cards.saveAndFlush(new BoardCard(boardId, columnId, position, draft));
        return view(board);
    }

    public BoardView updateCard(UUID cardId, long version, CardDraft draft) {
        requireEditor();
        BoardCard card = loadCard(cardId);
        if (card.getVersion() == null || card.getVersion() != version) {
            throw new ApiException(
                    ErrorCode.VERSION_CONFLICT,
                    "Outra pessoa alterou este card enquanto você editava. Recarregue para ver a versão atual.");
        }
        validateLinks(draft);
        card.update(draft);
        cards.flush();
        return view(load(card.getBoardId()));
    }

    /** Leva o card para a coluna e a posição (começa em 0), renumerando a coluna de origem e a de destino. */
    public BoardView moveCard(UUID cardId, UUID columnId, int position) {
        requireEditor();
        BoardCard card = loadCard(cardId);
        Board board = load(card.getBoardId());
        board.column(columnId);
        entityManager.lock(board, LockModeType.PESSIMISTIC_WRITE);

        UUID from = card.getColumnId();
        List<BoardCard> target = new ArrayList<>(cards.findByColumnIdOrderByPosition(columnId));
        target.removeIf(c -> c.getId().equals(cardId));
        target.add(Math.clamp(position, 0, target.size()), card);
        for (int i = 0; i < target.size(); i++) {
            target.get(i).placeAt(columnId, i);
        }
        if (!from.equals(columnId)) {
            List<BoardCard> source = new ArrayList<>(cards.findByColumnIdOrderByPosition(from));
            source.removeIf(c -> c.getId().equals(cardId));
            for (int i = 0; i < source.size(); i++) {
                source.get(i).placeAt(from, i);
            }
        }
        cards.flush();
        return view(board);
    }

    public BoardView deleteCard(UUID cardId) {
        requireEditor();
        BoardCard card = loadCard(cardId);
        Board board = load(card.getBoardId());
        entityManager.lock(board, LockModeType.PESSIMISTIC_WRITE);
        cards.delete(card);
        cards.flush();
        List<BoardCard> rest = cards.findByColumnIdOrderByPosition(card.getColumnId());
        for (int i = 0; i < rest.size(); i++) {
            rest.get(i).placeAt(card.getColumnId(), i);
        }
        cards.flush();
        return view(board);
    }

    private Board load(UUID boardId) {
        return boards.findById(boardId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Quadro não encontrado."));
    }

    private BoardCard loadCard(UUID cardId) {
        return cards.findById(cardId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Card não encontrado."));
    }

    private void requireEditor() {
        CurrentUser.get().requireCanEditOwnedBy(null);
    }

    private Board editable(UUID boardId, long version) {
        requireEditor();
        Board board = load(boardId);
        if (board.getVersion() == null || board.getVersion() != version) {
            throw new ApiException(
                    ErrorCode.VERSION_CONFLICT,
                    "Outra pessoa alterou este quadro enquanto você editava. Recarregue para ver a versão atual.");
        }
        return board;
    }

    /** Colunas ficam em tabela própria: a versão do quadro sobe na hora para a resposta já sair com ela. */
    private Board structureChange(UUID boardId, long version) {
        Board board = editable(boardId, version);
        entityManager.lock(board, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        return board;
    }

    private void validateLinks(CardDraft draft) {
        if (draft.ownerId() != null && users.find(draft.ownerId()).isEmpty()) {
            throw new DomainRuleViolation("OWNER_NOT_FOUND", "Responsável não encontrado.");
        }
        if (draft.tenderId() != null && tenders.find(draft.tenderId()).isEmpty()) {
            throw new DomainRuleViolation("TENDER_NOT_FOUND", "Licitação não encontrada.");
        }
    }

    private BoardView view(Board board) {
        List<BoardCard> all = cards.findByBoardIdOrderByPosition(board.getId());
        Set<UUID> ownerIds =
                all.stream().map(BoardCard::getOwnerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> tenderIds = all.stream()
                .map(BoardCard::getTenderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, UserSummary> owners = users.findAll(ownerIds);
        Map<UUID, TenderRef> linked = tenders.findAll(tenderIds);
        Map<UUID, List<BoardCard>> byColumn = all.stream().collect(Collectors.groupingBy(BoardCard::getColumnId));
        LocalDate today = LocalDate.now(clock);

        List<BoardView.Column> columns = new ArrayList<>();
        for (BoardColumn column : board.getColumns()) {
            List<BoardView.Card> columnCards = byColumn.getOrDefault(column.getId(), List.of()).stream()
                    .map(c -> card(c, lookup(owners, c.getOwnerId()), lookup(linked, c.getTenderId()), today))
                    .toList();
            BigDecimal total = columnCards.stream()
                    .filter(c -> c.tender() != null)
                    .map(c -> c.tender().totalValue())
                    .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
            columns.add(new BoardView.Column(
                    column.getId(), column.getName(), column.getPosition(), columnCards.size(), total, columnCards));
        }
        return new BoardView(
                board.getId(),
                board.getName(),
                board.getVersion() == null ? 0 : board.getVersion(),
                CurrentUser.get().canEditOwnedBy(null),
                columns);
    }

    /** Os mapas vêm de {@code Map.of()}, que não aceita {@code get(null)}; card sem responsável ou licitação é normal. */
    private static <T> T lookup(Map<UUID, T> map, UUID id) {
        return id == null ? null : map.get(id);
    }

    private static BoardView.Card card(BoardCard c, UserSummary owner, TenderRef tender, LocalDate today) {
        return new BoardView.Card(
                c.getId(),
                c.getColumnId(),
                c.getPosition(),
                c.getTitle(),
                c.getNotes(),
                owner == null ? null : new BoardView.Owner(owner.id(), owner.name(), owner.initials()),
                c.getDueDate(),
                c.isOverdue(today),
                tender == null
                        ? null
                        : new BoardView.TenderLink(
                                tender.id(),
                                tender.agencyName(),
                                tender.identification(),
                                tender.totalValue(),
                                tender.status()),
                c.getVersion() == null ? 0 : c.getVersion());
    }
}
