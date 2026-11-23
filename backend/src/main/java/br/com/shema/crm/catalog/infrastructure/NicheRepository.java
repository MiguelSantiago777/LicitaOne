package br.com.shema.crm.catalog.infrastructure;

import br.com.shema.crm.catalog.domain.Niche;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NicheRepository extends JpaRepository<Niche, UUID> {

    Optional<Niche> findByNameKey(String nameKey);

    List<Niche> findAllByOrderByNameKey();

    /** Fornecedores ativos por nicho, numa consulta só. */
    @Query(value = """
            select sn.niche_id as nicheId, count(*) as total
              from supplier_niches sn
              join suppliers s on s.id = sn.supplier_id
             where s.archived_at is null
             group by sn.niche_id
            """, nativeQuery = true)
    List<NicheCount> countActiveSuppliers();

    /** Produtos ativos por nicho, numa consulta só. */
    @Query(value = """
            select p.niche_id as nicheId, count(*) as total
              from products p
             where p.archived_at is null
             group by p.niche_id
            """, nativeQuery = true)
    List<NicheCount> countActiveProducts();

    interface NicheCount {
        UUID getNicheId();

        long getTotal();
    }
}
