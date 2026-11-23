package br.com.shema.crm.catalog.infrastructure;

import br.com.shema.crm.catalog.domain.Product;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    /** Produtos que têm preço do fornecedor (inclusive os arquivados, que a tela marca), por nome. */
    @Query("select distinct p from Product p join p.offers o where o.supplierId = :supplierId order by p.name")
    List<Product> findOfferedBy(@Param("supplierId") UUID supplierId);
}
