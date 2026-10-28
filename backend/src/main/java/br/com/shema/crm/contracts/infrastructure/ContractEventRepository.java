package br.com.shema.crm.contracts.infrastructure;

import br.com.shema.crm.contracts.domain.ContractEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractEventRepository extends JpaRepository<ContractEvent, UUID> {

    List<ContractEvent> findByTenderIdOrderByOccurredAtDesc(UUID tenderId);
}
