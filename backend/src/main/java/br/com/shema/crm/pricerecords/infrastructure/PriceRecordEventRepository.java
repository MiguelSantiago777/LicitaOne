package br.com.shema.crm.pricerecords.infrastructure;

import br.com.shema.crm.pricerecords.domain.PriceRecordEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceRecordEventRepository extends JpaRepository<PriceRecordEvent, UUID> {

    List<PriceRecordEvent> findByTenderIdOrderByOccurredAtDesc(UUID tenderId);
}
