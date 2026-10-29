package br.com.shema.crm.pricerecords.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Um arquivo enviado para um {@link Document}. Imutável: versão nova é sempre outra linha. */
@Entity
@Table(name = "document_versions")
public class DocumentVersion {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private int number;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, updatable = false, length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(nullable = false, updatable = false, length = 64)
    private String sha256;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 300)
    private String storageKey;

    @Column(name = "uploaded_by", updatable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_by_name", updatable = false, length = 120)
    private String uploadedByName;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    protected DocumentVersion() {}

    DocumentVersion(
            int number,
            FileInspection.Accepted file,
            long sizeBytes,
            String sha256,
            String storageKey,
            UUID uploadedBy,
            String uploadedByName,
            Instant uploadedAt) {
        this.id = UUID.randomUUID();
        this.number = number;
        this.fileName = file.fileName();
        this.contentType = file.contentType();
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.uploadedBy = uploadedBy;
        this.uploadedByName = uploadedByName;
        this.uploadedAt = uploadedAt;
    }

    public UUID getId() {
        return id;
    }

    public int getNumber() {
        return number;
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

    public String getSha256() {
        return sha256;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public String getUploadedByName() {
        return uploadedByName;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
