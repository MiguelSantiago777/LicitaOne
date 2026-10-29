package br.com.shema.crm.pricerecords.infrastructure;

import br.com.shema.crm.pricerecords.domain.Document;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /** Documentos ativos, com as versões juntas (evita uma consulta por documento). */
    @EntityGraph(attributePaths = "versions")
    List<Document> findByTenderIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID tenderId);
}
