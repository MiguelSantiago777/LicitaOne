package br.com.shema.crm.pricerecords.application;

import br.com.shema.crm.pricerecords.DocumentFiles;
import br.com.shema.crm.pricerecords.domain.Document;
import br.com.shema.crm.pricerecords.domain.DocumentVersion;
import br.com.shema.crm.pricerecords.infrastructure.DocumentRepository;
import br.com.shema.crm.shared.storage.FileStorage;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import br.com.shema.crm.tenders.TenderLifecycle;
import br.com.shema.crm.tenders.TenderRef;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class DocumentFilesService implements DocumentFiles {

    private final DocumentRepository documents;
    private final TenderLifecycle tenders;
    private final FileStorage storage;

    DocumentFilesService(DocumentRepository documents, TenderLifecycle tenders, FileStorage storage) {
        this.documents = documents;
        this.tenders = tenders;
        this.storage = storage;
    }

    @Override
    public Optional<DocumentFile> current(UUID documentId) {
        return documents.findById(documentId).filter(d -> !d.isDeleted()).map(this::file);
    }

    @Override
    public byte[] content(UUID documentId, int versionNumber) {
        Document document = documents
                .findById(documentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Documento não encontrado."));
        return storage.get(document.version(versionNumber).getStorageKey());
    }

    private DocumentFile file(Document d) {
        DocumentVersion v = d.version(d.getCurrentVersion());
        UUID ownerId = tenders.find(d.getTenderId()).map(TenderRef::ownerId).orElse(null);
        return new DocumentFile(
                d.getId(),
                d.getTenderId(),
                ownerId,
                d.getKind().name(),
                d.getTitle(),
                v.getNumber(),
                v.getFileName(),
                v.getContentType(),
                v.getSizeBytes());
    }
}
