package br.com.shema.crm.imports.infrastructure;

import br.com.shema.crm.imports.domain.ImportJob;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

    List<ImportJob> findTop20ByContractIdOrderByCreatedAtDesc(UUID contractId);

    long countByStatus(ImportJob.Status status);
}
