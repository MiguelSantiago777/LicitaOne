package br.com.shema.crm.pricerecords.domain;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Documento de uma licitação (ata, contrato, empenho, atestado, edital...). Cada envio de arquivo é uma versão nova;
 * nenhuma é sobrescrita. Apagar é lógico: o documento some das listas, mas o registro e os arquivos ficam.
 */
@Entity
@Table(name = "documents")
public class Document extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "tender_id", nullable = false, updatable = false)
    private UUID tenderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DocumentKind kind;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "document_date")
    private LocalDate documentDate;

    @Column(name = "current_version", nullable = false)
    private int currentVersion;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 120)
    private String deletedBy;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "document_id", nullable = false, updatable = false)
    @OrderBy("number desc")
    private List<DocumentVersion> versions = new ArrayList<>();

    protected Document() {}

    public Document(UUID tenderId, DocumentKind kind, String title, LocalDate documentDate) {
        this.id = UUID.randomUUID();
        this.tenderId = tenderId;
        describe(kind, title, documentDate);
    }

    public void describe(DocumentKind kind, String title, LocalDate documentDate) {
        requireActive();
        if (kind == null) {
            throw new DomainRuleViolation("REQUIRED", "Escolha o tipo do documento.");
        }
        if (title == null || title.isBlank()) {
            throw new DomainRuleViolation("REQUIRED", "Informe o título do documento.");
        }
        String trimmed = title.trim().replaceAll("\\s+", " ");
        if (trimmed.length() > 200) {
            throw new DomainRuleViolation("TOO_LONG", "Use no máximo 200 caracteres no título.");
        }
        this.kind = kind;
        this.title = trimmed;
        this.documentDate = documentDate;
    }

    /**
     * Registra um arquivo novo como próxima versão. A chave no armazenamento é derivada do documento e do número da
     * versão, nunca do nome enviado.
     */
    public DocumentVersion addVersion(
            FileInspection.Accepted file, long size, String sha256, UUID by, String byName, Instant at) {
        requireActive();
        int number = currentVersion + 1;
        String key = "documents/" + tenderId + "/" + id + "/v" + number;
        DocumentVersion version = new DocumentVersion(number, file, size, sha256, key, by, byName, at);
        versions.addFirst(version);
        this.currentVersion = number;
        return version;
    }

    public DocumentVersion version(int number) {
        return versions.stream()
                .filter(v -> v.getNumber() == number)
                .findFirst()
                .orElseThrow(() -> new DomainRuleViolation("VERSION_NOT_FOUND", "Versão não encontrada."));
    }

    public void delete(String byName, Instant at) {
        requireActive();
        this.deletedAt = at;
        this.deletedBy = byName;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private void requireActive() {
        if (deletedAt != null) {
            throw new DomainRuleViolation("DOCUMENT_DELETED", "Este documento foi apagado.");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenderId() {
        return tenderId;
    }

    public DocumentKind getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getDocumentDate() {
        return documentDate;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    /** Da mais nova para a mais antiga. */
    public List<DocumentVersion> getVersions() {
        return List.copyOf(versions);
    }
}
