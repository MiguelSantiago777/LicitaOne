package br.com.shema.crm.deals.infrastructure;

import br.com.shema.crm.deals.domain.Pipeline;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineRepository extends JpaRepository<Pipeline, UUID> {

    /** O funil de vendas (o seed cria um; o modelo já aceita outros no futuro). */
    Optional<Pipeline> findFirstByOrderByCreatedAtAsc();
}
