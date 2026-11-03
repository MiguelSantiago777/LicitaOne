package br.com.shema.crm.extraction.infrastructure;

import br.com.shema.crm.extraction.domain.Extraction;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractionRepository extends JpaRepository<Extraction, UUID> {

    Optional<Extraction> findFirstByDocumentIdOrderByCreatedAtDesc(UUID documentId);

    Optional<Extraction> findFirstByDocumentIdAndDocumentVersionAndStatusIn(
            UUID documentId, int documentVersion, Collection<Extraction.Status> statuses);

    long countByStatus(Extraction.Status status);
}
