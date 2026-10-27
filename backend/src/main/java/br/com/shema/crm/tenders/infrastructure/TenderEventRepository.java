package br.com.shema.crm.tenders.infrastructure;

import br.com.shema.crm.tenders.domain.TenderEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenderEventRepository extends JpaRepository<TenderEvent, UUID> {

    List<TenderEvent> findByTenderIdOrderByOccurredAtDesc(UUID tenderId);
}
