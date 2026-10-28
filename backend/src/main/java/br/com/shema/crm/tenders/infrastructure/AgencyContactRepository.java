package br.com.shema.crm.tenders.infrastructure;

import br.com.shema.crm.tenders.domain.AgencyContact;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AgencyContactRepository extends JpaRepository<AgencyContact, UUID> {

    List<AgencyContact> findByAgencyIdOrderByPosition(UUID agencyId);

    @Modifying
    @Query("delete from AgencyContact c where c.agencyId = :agencyId")
    void deleteByAgencyId(UUID agencyId);
}
