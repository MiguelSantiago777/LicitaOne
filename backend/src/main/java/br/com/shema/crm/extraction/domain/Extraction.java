package br.com.shema.crm.extraction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Uma leitura de documento com IA: na fila → lendo → concluída ou com falha. O resultado ({@link ReadingResult} em
 * JSON) é só uma sugestão; nada da ata ou do contrato muda aqui.
 */
@Entity
@Table(name = "document_extractions")
public class Extraction {

    public enum Kind {
        ATA,
        CONTRATO
    }

    public enum Status {
        QUEUED,
        RUNNING,
        DONE,
        FAILED
    }

    @Id
    private UUID id;

    @Version
    private Long version;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "document_version", nullable = false, updatable = false)
    private int documentVersion;

    @Column(name = "tender_id", nullable = false, updatable = false)
    private UUID tenderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(columnDefinition = "text")
    private String result;

    @Column(length = 60)
    private String model;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_by_id", nullable = false, updatable = false)
    private UUID createdById;

    @Column(name = "created_by_name", length = 120, updatable = false)
    private String createdByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected Extraction() {}

    public static Extraction queue(
            UUID documentId,
            int documentVersion,
            UUID tenderId,
            Kind kind,
            UUID createdById,
            String createdByName,
            Instant now) {
        Extraction e = new Extraction();
        e.id = UUID.randomUUID();
        e.documentId = documentId;
        e.documentVersion = documentVersion;
        e.tenderId = tenderId;
        e.kind = kind;
        e.status = Status.QUEUED;
        e.createdById = createdById;
        e.createdByName = createdByName;
        e.createdAt = now;
        return e;
    }

    /**
     * Começa a leitura. Devolve {@code false} se ela já terminou (mensagem repetida). Uma leitura em andamento pode
     * recomeçar: se a mensagem voltou, a tentativa anterior morreu no meio.
     */
    public boolean start(Instant now) {
        if (isFinished()) {
            return false;
        }
        this.status = Status.RUNNING;
        this.startedAt = now;
        return true;
    }

    public void finish(String resultJson, String model, long inputTokens, long outputTokens, Instant now) {
        this.result = resultJson;
        this.model = truncate(model, 60);
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.status = Status.DONE;
        this.finishedAt = now;
    }

    /** @param model e tokens podem ser nulos quando a falha veio antes de o modelo responder */
    public void fail(String message, String model, Long inputTokens, Long outputTokens, Instant now) {
        this.errorMessage = truncate(message, 1000);
        this.model = truncate(model, 60);
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.status = Status.FAILED;
        this.finishedAt = now;
    }

    public boolean isFinished() {
        return status == Status.DONE || status == Status.FAILED;
    }

    private static String truncate(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }

    public UUID getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getDocumentVersion() {
        return documentVersion;
    }

    public UUID getTenderId() {
        return tenderId;
    }

    public Kind getKind() {
        return kind;
    }

    public Status getStatus() {
        return status;
    }

    public String getResult() {
        return result;
    }

    public String getModel() {
        return model;
    }

    public Long getInputTokens() {
        return inputTokens;
    }

    public Long getOutputTokens() {
        return outputTokens;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public UUID getCreatedById() {
        return createdById;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
