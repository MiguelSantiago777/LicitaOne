package br.com.shema.crm.tenders.infrastructure;

import br.com.shema.crm.tenders.domain.Tender;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface TenderRepository extends JpaRepository<Tender, UUID>, JpaSpecificationExecutor<Tender> {

    /** Carrega o órgão junto (evita N+1 na listagem). */
    @Override
    @EntityGraph(attributePaths = "agency")
    Page<Tender> findAll(Specification<Tender> spec, Pageable pageable);

    /** Carrega o órgão junto: a resposta é montada fora da transação (open-in-view desligado). */
    @Override
    @EntityGraph(attributePaths = "agency")
    Optional<Tender> findById(UUID id);

    @Query("select distinct t.materialType from Tender t order by t.materialType")
    List<String> distinctMaterialTypes();

    @Query("select distinct t.portal from Tender t where t.portal is not null order by t.portal")
    List<String> distinctPortals();

    long countByAgencyId(UUID agencyId);

    @EntityGraph(attributePaths = "agency")
    List<Tender> findAllWithAgencyByIdIn(Collection<UUID> ids);

    /** Vencidas (ou aguardando assinatura) até a data, ainda sem contrato assinado: o alerta da seção 5.4. */
    @EntityGraph(attributePaths = "agency")
    @Query("""
            select t from Tender t
            where t.status in (
                br.com.shema.crm.tenders.domain.TenderStatus.VENCIDA,
                br.com.shema.crm.tenders.domain.TenderStatus.AGUARDANDO_ASSINATURA)
              and t.wonAt <= :wonOnOrBefore
            order by t.wonAt
            """)
    List<Tender> awaitingContractWonOnOrBefore(LocalDate wonOnOrBefore);

    interface AgencyTotal {
        UUID getAgencyId();

        String getName();

        String getCity();

        String getState();

        long getTenders();

        BigDecimal getValue();
    }

    /** Órgãos pelo valor das licitações vencidas (em disputa e perdidas não contam), do maior para o menor. */
    @Query(value = """
                    select a.id as agencyId, a.name as name, a.city as city, a.state as state,
                           count(*) as tenders, sum(t.total_value) as value
                    from tenders t join agencies a on a.id = t.agency_id
                    where t.status not in ('EM_DISPUTA', 'PERDIDA')
                    group by a.id, a.name, a.city, a.state
                    order by value desc, a.name
                    limit :limit
                    """, nativeQuery = true)
    List<AgencyTotal> topAgenciesByWonValue(int limit);
}
