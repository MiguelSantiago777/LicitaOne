package br.com.shema.crm.pricerecords.application;

import br.com.shema.crm.pricerecords.domain.Document;
import br.com.shema.crm.pricerecords.domain.DocumentKind;
import br.com.shema.crm.pricerecords.domain.DocumentVersion;
import br.com.shema.crm.pricerecords.domain.FileInspection;
import br.com.shema.crm.pricerecords.domain.PriceRecordEvent;
import br.com.shema.crm.pricerecords.infrastructure.DocumentRepository;
import br.com.shema.crm.pricerecords.infrastructure.PriceRecordEventRepository;
import br.com.shema.crm.shared.security.CurrentUser;
import br.com.shema.crm.shared.storage.FileStorage;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import br.com.shema.crm.tenders.TenderLifecycle;
import br.com.shema.crm.tenders.TenderRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Documentos das licitações. Quem pode alterar a licitação (responsável ou administrador) envia, troca a versão,
 * renomeia e apaga; todos os usuários consultam e baixam. Apagar é lógico e os arquivos ficam no armazenamento.
 */
@Service
@Transactional
public class DocumentService {

    @Schema(name = "DocumentResponse")
    public record DocumentView(
            UUID id,
            UUID tenderId,
            DocumentKind kind,
            String kindLabel,
            String title,
            LocalDate documentDate,
            int currentVersion,
            List<VersionView> versions,
            long version,
            boolean canEdit) {}

    @Schema(name = "DocumentVersionResponse")
    public record VersionView(
            int number, String fileName, String contentType, long sizeBytes, String uploadedBy, Instant uploadedAt) {}

    /** Um arquivo pronto para download. */
    public record FileDownload(String fileName, String contentType, byte[] content) {}

    private final DocumentRepository documents;
    private final PriceRecordEventRepository events;
    private final TenderLifecycle tenders;
    private final FileStorage storage;
    private final Clock clock;

    public DocumentService(
            DocumentRepository documents,
            PriceRecordEventRepository events,
            TenderLifecycle tenders,
            FileStorage storage,
            Clock clock) {
        this.documents = documents;
        this.events = events;
        this.tenders = tenders;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DocumentView> list(UUID tenderId) {
        TenderRef tender = tender(tenderId);
        boolean canEdit = CurrentUser.get().canEditOwnedBy(tender.ownerId());
        return documents.findByTenderIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenderId).stream()
                .map(d -> view(d, canEdit))
                .toList();
    }

    public DocumentView upload(
            UUID tenderId, DocumentKind kind, String title, LocalDate documentDate, String fileName, byte[] content) {
        TenderRef tender = tender(tenderId);
        CurrentUser user = CurrentUser.get();
        user.requireCanEditOwnedBy(tender.ownerId());
        FileInspection.Accepted file = FileInspection.inspect(fileName, content);
        Document document = new Document(tenderId, kind, title, documentDate);
        DocumentVersion version =
                document.addVersion(file, content.length, sha256(content), user.id(), user.name(), now());
        documents.saveAndFlush(document);
        storage.put(version.getStorageKey(), content);
        record(
                tenderId,
                PriceRecordEvent.Type.DOCUMENT_ADDED,
                kind.label() + " \"" + document.getTitle() + "\" enviado (" + file.fileName() + ")",
                user);
        return view(document, true);
    }

    public DocumentView addVersion(UUID documentId, long expectedVersion, String fileName, byte[] content) {
        CurrentUser user = CurrentUser.get();
        Document document = editable(documentId, expectedVersion, user);
        FileInspection.Accepted file = FileInspection.inspect(fileName, content);
        DocumentVersion version =
                document.addVersion(file, content.length, sha256(content), user.id(), user.name(), now());
        documents.saveAndFlush(document);
        storage.put(version.getStorageKey(), content);
        record(
                document.getTenderId(),
                PriceRecordEvent.Type.DOCUMENT_VERSION_ADDED,
                "Versão " + version.getNumber() + " de \"" + document.getTitle() + "\" (" + file.fileName() + ")",
                user);
        return view(document, true);
    }

    public DocumentView describe(
            UUID documentId, long expectedVersion, DocumentKind kind, String title, LocalDate documentDate) {
        CurrentUser user = CurrentUser.get();
        Document document = editable(documentId, expectedVersion, user);
        String before = document.getKind().label() + " \"" + document.getTitle() + "\"";
        document.describe(kind, title, documentDate);
        documents.flush();
        record(
                document.getTenderId(),
                PriceRecordEvent.Type.DOCUMENT_UPDATED,
                before + " passou a " + document.getKind().label() + " \"" + document.getTitle() + "\"",
                user);
        return view(document, true);
    }

    public void delete(UUID documentId, long expectedVersion) {
        CurrentUser user = CurrentUser.get();
        Document document = editable(documentId, expectedVersion, user);
        document.delete(user.name(), now());
        documents.flush();
        record(
                document.getTenderId(),
                PriceRecordEvent.Type.DOCUMENT_DELETED,
                document.getKind().label() + " \"" + document.getTitle()
                        + "\" apagado (os arquivos continuam guardados)",
                user);
    }

    @Transactional(readOnly = true)
    public FileDownload download(UUID documentId, int number) {
        Document document = load(documentId);
        if (document.isDeleted()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Documento não encontrado.");
        }
        DocumentVersion version = document.version(number);
        return new FileDownload(version.getFileName(), version.getContentType(), storage.get(version.getStorageKey()));
    }

    /** Documento ativo da licitação (para a ata apontar para o arquivo dela). */
    @Transactional(readOnly = true)
    public Document activeOf(UUID tenderId, UUID documentId) {
        Document document = load(documentId);
        if (document.isDeleted() || !document.getTenderId().equals(tenderId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Documento não encontrado nesta licitação.");
        }
        return document;
    }

    private Document load(UUID id) {
        return documents
                .findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Documento não encontrado."));
    }

    private Document editable(UUID documentId, long expectedVersion, CurrentUser user) {
        Document document = load(documentId);
        user.requireCanEditOwnedBy(tender(document.getTenderId()).ownerId());
        if (document.getVersion() == null || document.getVersion() != expectedVersion) {
            throw new ApiException(
                    ErrorCode.VERSION_CONFLICT,
                    "Outra pessoa alterou este documento enquanto você editava. Recarregue para ver a versão atual.");
        }
        return document;
    }

    private TenderRef tender(UUID tenderId) {
        return tenders.find(tenderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Licitação não encontrada."));
    }

    private void record(UUID tenderId, PriceRecordEvent.Type type, String summary, CurrentUser user) {
        events.save(new PriceRecordEvent(tenderId, type, summary, user.id(), user.name(), now()));
    }

    private Instant now() {
        return clock.instant();
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static DocumentView view(Document d, boolean canEdit) {
        return new DocumentView(
                d.getId(),
                d.getTenderId(),
                d.getKind(),
                d.getKind().label(),
                d.getTitle(),
                d.getDocumentDate(),
                d.getCurrentVersion(),
                d.getVersions().stream()
                        .map(v -> new VersionView(
                                v.getNumber(),
                                v.getFileName(),
                                v.getContentType(),
                                v.getSizeBytes(),
                                v.getUploadedByName(),
                                v.getUploadedAt()))
                        .toList(),
                d.getVersion() == null ? 0 : d.getVersion(),
                canEdit);
    }
}
