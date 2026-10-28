package br.com.shema.crm.contracts.infrastructure;

import br.com.shema.crm.contracts.domain.Contract;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ContractRepository extends JpaRepository<Contract, UUID> {

    List<Contract> findByTenderIdOrderBySignedAtAscCreatedAtAsc(UUID tenderId);

    boolean existsByTenderIdAndClosedAtIsNull(UUID tenderId);

    @Query("select max(c.closedAt) from Contract c where c.tenderId = :tenderId")
    LocalDate lastClosedAt(UUID tenderId);
}
