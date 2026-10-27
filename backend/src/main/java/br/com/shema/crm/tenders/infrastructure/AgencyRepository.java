package br.com.shema.crm.tenders.infrastructure;

import br.com.shema.crm.tenders.domain.Agency;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AgencyRepository extends JpaRepository<Agency, UUID> {

    @Query("""
            select a from Agency a
            where :q is null
               or lower(a.name) like lower(concat('%', cast(:q as string), '%'))
               or lower(a.city) like lower(concat('%', cast(:q as string), '%'))
            """)
    Page<Agency> search(String q, Pageable pageable);

    boolean existsByNameIgnoreCaseAndCityIgnoreCase(String name, String city);
}
