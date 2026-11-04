package br.com.shema.crm.extraction.infrastructure;

import br.com.shema.crm.extraction.domain.Extraction;
import br.com.shema.crm.extraction.domain.OrderReading;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderReadingRepository extends JpaRepository<OrderReading, UUID> {

    Optional<OrderReading> findFirstByContractIdAndDocumentIdAndDocumentVersionAndStatusIn(
            UUID contractId, UUID documentId, Integer documentVersion, Collection<Extraction.Status> statuses);

    long countByStatus(Extraction.Status status);
}
