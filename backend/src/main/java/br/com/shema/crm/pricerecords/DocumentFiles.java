package br.com.shema.crm.pricerecords;

import java.util.Optional;
import java.util.UUID;

/** API pública dos arquivos dos documentos, para a leitura com IA (ADR 0016) sem conhecer a entidade. */
public interface DocumentFiles {

    /**
     * Versão atual de um documento ativo.
     *
     * @param kind nome do tipo ({@code ATA}, {@code CONTRATO}, {@code EDITAL}...)
     * @param ownerId responsável pela licitação, para conferir quem pode alterar
     */
    record DocumentFile(
            UUID documentId,
            UUID tenderId,
            UUID ownerId,
            String kind,
            String title,
            int versionNumber,
            String fileName,
            String contentType,
            long sizeBytes) {}

    /** Versão atual do documento, ou vazio se ele não existe ou foi apagado. */
    Optional<DocumentFile> current(UUID documentId);

    /** Conteúdo de uma versão do documento. */
    byte[] content(UUID documentId, int versionNumber);
}
