package br.com.shema.crm.extraction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Leitura de um pedido com IA (Fase 8): um documento (PDF ou foto) ou um texto colado, lido contra os itens de um
 * contrato. O resultado ({@link OrderReadingResult} em JSON) é só uma sugestão para o formulário de pedido.
 *
 * <p>O texto colado fica guardado só até a leitura terminar: ao concluir ou falhar, ele é apagado.
 */
@Entity
@Table(name = "order_readings")
public class OrderReading {

    public enum Source {
        DOCUMENT,
        TEXT
    }

    @Id
    private UUID id;

    @Version
    private Long version;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Column(name = "tender_id", nullable = false, updatable = false)
    private UUID tenderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Source source;

    @Column(name = "document_id", updatable = false)
    private UUID documentId;

    @Column(name = "document_version", updatable = false)
    private Integer documentVersion;

    @Column(name = "source_text", columnDefinition = "text")
    private String sourceText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Extraction.Status status;

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

    protected OrderReading() {}

    public static OrderReading ofDocument(
            UUID contractId,
            UUID tenderId,
            UUID documentId,
            int documentVersion,
            UUID createdById,
            String createdByName,
            Instant now) {
        OrderReading r = queued(contractId, tenderId, Source.DOCUMENT, createdById, createdByName, now);
        r.documentId = Objects.requireNonNull(documentId, "documentId");
        r.documentVersion = documentVersion;
        return r;
    }

    public static OrderReading ofText(
            UUID contractId, UUID tenderId, String text, UUID createdById, String createdByName, Instant now) {
        OrderReading r = queued(contractId, tenderId, Source.TEXT, createdById, createdByName, now);
        r.sourceText = Objects.requireNonNull(text, "text");
        return r;
    }

    private static OrderReading queued(
            UUID contractId, UUID tenderId, Source source, UUID createdById, String createdByName, Instant now) {
        OrderReading r = new OrderReading();
        r.id = UUID.randomUUID();
        r.contractId = Objects.requireNonNull(contractId, "contractId");
        r.tenderId = Objects.requireNonNull(tenderId, "tenderId");
        r.source = source;
        r.status = Extraction.Status.QUEUED;
        r.createdById = createdById;
        r.createdByName = createdByName;
        r.createdAt = now;
        return r;
    }

    /** Começa a leitura; {@code false} se ela já terminou (mensagem repetida). */
    public boolean start(Instant now) {
        if (isFinished()) {
            return false;
        }
        this.status = Extraction.Status.RUNNING;
        this.startedAt = now;
        return true;
    }

    public void finish(String resultJson, String model, long inputTokens, long outputTokens, Instant now) {
        this.result = resultJson;
        this.model = truncate(model, 60);
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.status = Extraction.Status.DONE;
        this.finishedAt = now;
        this.sourceText = null;
    }

    public void fail(String message, String model, Long inputTokens, Long outputTokens, Instant now) {
        this.errorMessage = truncate(message, 1000);
        this.model = truncate(model, 60);
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.status = Extraction.Status.FAILED;
        this.finishedAt = now;
        this.sourceText = null;
    }

    public boolean isFinished() {
        return status == Extraction.Status.DONE || status == Extraction.Status.FAILED;
    }

    private static String truncate(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }

    public UUID getId() {
        return id;
    }

    public UUID getContractId() {
        return contractId;
    }

    public UUID getTenderId() {
        return tenderId;
    }

    public Source getSource() {
        return source;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public Integer getDocumentVersion() {
        return documentVersion;
    }

    public String getSourceText() {
        return sourceText;
    }

    public Extraction.Status getStatus() {
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

    public String getCreatedByName() {
        return createdByName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
