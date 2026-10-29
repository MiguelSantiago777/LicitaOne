package br.com.shema.crm.tenders.infrastructure;

import br.com.shema.crm.tenders.domain.Agency;
import java.util.List;
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

    @Query("""
            select a from Agency a
            where a.sphere = br.com.shema.crm.tenders.domain.Sphere.MUNICIPAL
              and (cast(:state as string) is null or a.state = :state)
              and not exists (
                  select 1 from Tender t
                  where t.agency = a
                    and t.status not in (
                        br.com.shema.crm.tenders.domain.TenderStatus.EM_DISPUTA,
                        br.com.shema.crm.tenders.domain.TenderStatus.PERDIDA))
            order by a.name
            """)
    List<Agency> municipalitiesWithoutWonTender(String state);
}
