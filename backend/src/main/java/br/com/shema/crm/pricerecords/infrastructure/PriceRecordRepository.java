package br.com.shema.crm.pricerecords.infrastructure;

import br.com.shema.crm.pricerecords.domain.PriceRecord;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PriceRecordRepository extends JpaRepository<PriceRecord, UUID> {

    Optional<PriceRecord> findByTenderId(UUID tenderId);

    List<PriceRecord> findByTenderIdIn(Collection<UUID> tenderIds);

    List<PriceRecord> findAllByOrderByValidUntilAsc();

    /** Órgãos que já aderiram a alguma ata (não são mais "sem ata"). */
    @Query("select distinct c.agencyId from PriceRecord p join p.caronas c")
    List<UUID> agenciesWithCarona();
}
