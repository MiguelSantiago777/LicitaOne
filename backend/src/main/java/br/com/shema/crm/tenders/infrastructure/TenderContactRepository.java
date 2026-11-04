package br.com.shema.crm.tenders.infrastructure;

import br.com.shema.crm.tenders.domain.TenderContact;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenderContactRepository extends JpaRepository<TenderContact, UUID> {

    List<TenderContact> findByTenderIdOrderByContactedAtDescCreatedAtDesc(UUID tenderId);

    List<TenderContact> findByTenderIdOrderByContactedAtDescCreatedAtDesc(UUID tenderId, Limit limit);
}
