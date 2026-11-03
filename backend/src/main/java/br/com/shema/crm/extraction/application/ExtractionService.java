package br.com.shema.crm.extraction.application;

import br.com.shema.crm.extraction.ExtractionRequested;
import br.com.shema.crm.extraction.domain.Extraction;
import br.com.shema.crm.extraction.domain.ReadingResult;
import br.com.shema.crm.extraction.infrastructure.ExtractionRepository;
import br.com.shema.crm.pricerecords.DocumentFiles;
import br.com.shema.crm.pricerecords.DocumentFiles.DocumentFile;
import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.messaging.EventMetadata;
import br.com.shema.crm.shared.security.CurrentUser;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pedido e consulta das leituras com IA. Quem pode alterar a licitação pede a leitura; todos consultam, como os
 * documentos. O PDF só sai do sistema quando alguém pede (botão "Ler com IA"), nunca no envio.
 */
@Service
@Transactional
public class ExtractionService {

    private static final List<Extraction.Status> IN_PROGRESS =
            List.of(Extraction.Status.QUEUED, Extraction.Status.RUNNING);

    @Schema(name = "ExtractionSettingsResponse")
    public record Settings(boolean enabled, String model, int maxFileMb) {}

    @Schema(name = "ExtractionResponse")
    public record ExtractionView(
            UUID id,
            UUID documentId,
            int documentVersion,
            UUID tenderId,
            Extraction.Kind kind,
            Extraction.Status status,
            ReadingResult result,
            String model,
            Long inputTokens,
            Long outputTokens,
            String errorMessage,
            String createdByName,
            Instant createdAt,
            Instant finishedAt) {}

    private final ExtractionRepository extractions;
    private final DocumentFiles files;
    private final DocumentReader reader;
    private final ExtractionProperties properties;
    private final ApplicationEventPublisher publisher;
    private final JsonMapper json;
    private final Clock clock;

    public ExtractionService(
            ExtractionRepository extractions,
            DocumentFiles files,
            DocumentReader reader,
            ExtractionProperties properties,
            ApplicationEventPublisher publisher,
            JsonMapper json,
            Clock clock) {
        this.extractions = extractions;
        this.files = files;
        this.reader = reader;
        this.properties = properties;
        this.publisher = publisher;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Settings settings() {
        return new Settings(reader.enabled(), reader.enabled() ? properties.model() : null, properties.maxFileMb());
    }

    /**
     * Põe a leitura da versão atual do documento na fila (202). Se já há uma leitura dessa versão em andamento,
     * devolve ela em vez de mandar o PDF de novo.
     */
    public ExtractionView request(UUID documentId) {
        CurrentUser user = CurrentUser.get();
        DocumentFile file = files.current(documentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Documento não encontrado."));
        user.requireCanEditOwnedBy(file.ownerId());
        if (!reader.enabled()) {
            throw new DomainRuleViolation(
                    "AI_DISABLED", "A leitura com IA não está configurada neste ambiente. Fale com o administrador.");
        }
        Extraction.Kind kind = kindOf(file);
        if (!"application/pdf".equals(file.contentType())) {
            throw new DomainRuleViolation("AI_PDF_ONLY", "Por enquanto a IA só lê arquivos PDF.");
        }
        if (file.sizeBytes() > properties.maxFileBytes()) {
            throw new DomainRuleViolation(
                    "AI_FILE_TOO_LARGE",
                    "O PDF passa de " + properties.maxFileMb() + " MB, o máximo que a IA lê de uma vez.");
        }
        Optional<Extraction> running = extractions.findFirstByDocumentIdAndDocumentVersionAndStatusIn(
                documentId, file.versionNumber(), IN_PROGRESS);
        if (running.isPresent()) {
            return view(running.get());
        }
        Extraction extraction = Extraction.queue(
                documentId, file.versionNumber(), file.tenderId(), kind, user.id(), user.name(), clock.instant());
        extractions.saveAndFlush(extraction);
        publisher.publishEvent(new ExtractionRequested(EventMetadata.create(clock), extraction.getId()));
        return view(extraction);
    }

    @Transactional(readOnly = true)
    public ExtractionView get(UUID extractionId) {
        return view(extractions
                .findById(extractionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Leitura não encontrada.")));
    }

    /** A leitura mais recente do documento, de qualquer versão. */
    @Transactional(readOnly = true)
    public Optional<ExtractionView> latest(UUID documentId) {
        return extractions.findFirstByDocumentIdOrderByCreatedAtDesc(documentId).map(this::view);
    }

    String toJson(ReadingResult result) {
        return json.writeValueAsString(result);
    }

    private ExtractionView view(Extraction e) {
        ReadingResult result = e.getResult() == null ? null : json.readValue(e.getResult(), ReadingResult.class);
        return new ExtractionView(
                e.getId(),
                e.getDocumentId(),
                e.getDocumentVersion(),
                e.getTenderId(),
                e.getKind(),
                e.getStatus(),
                result,
                e.getModel(),
                e.getInputTokens(),
                e.getOutputTokens(),
                e.getErrorMessage(),
                e.getCreatedByName(),
                e.getCreatedAt(),
                e.getFinishedAt());
    }

    private static Extraction.Kind kindOf(DocumentFile file) {
        return switch (file.kind()) {
            case "ATA" -> Extraction.Kind.ATA;
            case "CONTRATO" -> Extraction.Kind.CONTRATO;
            default ->
                throw new DomainRuleViolation(
                        "AI_KIND_NOT_SUPPORTED", "Por enquanto a IA só lê documentos do tipo Ata ou Contrato.");
        };
    }
}
