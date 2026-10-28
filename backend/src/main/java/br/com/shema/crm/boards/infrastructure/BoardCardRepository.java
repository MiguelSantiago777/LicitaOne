package br.com.shema.crm.boards.infrastructure;

import br.com.shema.crm.boards.domain.BoardCard;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BoardCardRepository extends JpaRepository<BoardCard, UUID> {

    List<BoardCard> findByBoardIdOrderByPosition(UUID boardId);

    List<BoardCard> findByColumnIdOrderByPosition(UUID columnId);

    long countByColumnId(UUID columnId);

    long countByBoardId(UUID boardId);

    /** Quantos cards cada quadro tem, para a lista de quadros. */
    @Query("select c.boardId, count(c) from BoardCard c group by c.boardId")
    List<Object[]> countByBoard();
}
