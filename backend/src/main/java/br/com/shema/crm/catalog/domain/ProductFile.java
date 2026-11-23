package br.com.shema.crm.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Foto ou ficha técnica do produto. O conteúdo fica no FileStorage, na chave guardada aqui. Fica fora do agregado
 * {@link Product} de propósito: enviar um arquivo não muda a versão do produto, e quem está editando não toma conflito.
 */
@Entity
@Table(name = "product_files")
public class ProductFile {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProductFileKind kind;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 300, updatable = false)
    private String storageKey;

    @Column(name = "uploaded_by", length = 120)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected ProductFile() {}

    public ProductFile(
            UUID productId,
            ProductFileKind kind,
            CatalogFileInspection.Accepted file,
            long sizeBytes,
            String uploadedBy,
            Instant uploadedAt) {
        this.id = UUID.randomUUID();
        this.productId = productId;
        this.kind = kind;
        this.fileName = file.fileName();
        this.contentType = file.contentType();
        this.sizeBytes = sizeBytes;
        this.storageKey = "catalog/products/" + productId + "/" + id;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    public boolean isImage() {
        return contentType.startsWith("image/");
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public ProductFileKind getKind() {
        return kind;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
