package br.com.shema.crm.catalog.infrastructure;

import br.com.shema.crm.catalog.domain.Supplier;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, UUID>, JpaSpecificationExecutor<Supplier> {

    /** Produtos ativos que cada fornecedor da página tem com preço, numa consulta só. */
    @Query(value = """
            select o.supplier_id as supplierId, count(*) as total
              from product_offers o
              join products p on p.id = o.product_id
             where o.supplier_id in (:ids) and p.archived_at is null
             group by o.supplier_id
            """, nativeQuery = true)
    List<SupplierCount> countActiveProducts(@Param("ids") Collection<UUID> ids);

    /** Para escolher o fornecedor de um preço: os ativos, por nome. */
    List<Supplier> findByArchivedAtIsNullOrderByLegalName();

    interface SupplierCount {
        UUID getSupplierId();

        long getTotal();
    }
}
