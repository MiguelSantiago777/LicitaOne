package br.com.shema.crm.catalog.infrastructure;

import br.com.shema.crm.catalog.domain.ProductFile;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductFileRepository extends JpaRepository<ProductFile, UUID> {

    List<ProductFile> findByProductIdOrderByUploadedAtDesc(UUID productId);

    List<ProductFile> findByProductIdIn(Collection<UUID> productIds);

    long countByProductId(UUID productId);
}
