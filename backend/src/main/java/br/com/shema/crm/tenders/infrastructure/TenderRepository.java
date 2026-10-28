package br.com.shema.crm.tenders.infrastructure;

import br.com.shema.crm.tenders.domain.Tender;
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
}
