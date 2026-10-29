package br.com.shema.crm.tasks.infrastructure;

import br.com.shema.crm.tasks.domain.Task;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    @Query("select t from Task t where t.completedAt is null and t.dealId in :dealIds order by t.dueAt")
    List<Task> findOpenByDealIds(Collection<UUID> dealIds);

    long countByDealIdAndCompletedAtIsNull(UUID dealId);

    /** Abertas e atrasadas de uma vez, para o resumo no topo da lista (só do responsável, se informado). */
    @Query("""
            select count(t), coalesce(sum(case when t.dueAt < :now then 1 else 0 end), 0)
            from Task t
            where t.completedAt is null and (:ownerId is null or t.ownerId = :ownerId)
            """)
    List<Object[]> countOpen(UUID ownerId, OffsetDateTime now);
}
