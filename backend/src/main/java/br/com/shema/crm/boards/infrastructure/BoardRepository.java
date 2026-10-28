package br.com.shema.crm.boards.infrastructure;

import br.com.shema.crm.boards.domain.Board;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BoardRepository extends JpaRepository<Board, UUID> {

    List<Board> findAllByOrderByPositionAscNameAsc();

    @Query("select coalesce(max(b.position), -1) from Board b")
    int maxPosition();
}
