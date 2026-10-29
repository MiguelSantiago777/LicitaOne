package br.com.shema.crm.deals.infrastructure;

import br.com.shema.crm.deals.domain.Deal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealRepository extends JpaRepository<Deal, UUID> {

    List<Deal> findByPipelineIdOrderByPosition(UUID pipelineId);

    List<Deal> findByStageIdOrderByPosition(UUID stageId);

    long countByStageId(UUID stageId);
}
