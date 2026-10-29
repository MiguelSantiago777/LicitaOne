package br.com.shema.crm.pricerecords.api;

import br.com.shema.crm.pricerecords.application.DocumentService;
import br.com.shema.crm.pricerecords.application.DocumentService.DocumentView;
import br.com.shema.crm.pricerecords.application.DocumentService.FileDownload;
import br.com.shema.crm.pricerecords.domain.DocumentKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "documents", description = "Documentos das licitações")
class DocumentController {

    record DescribeRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version,

            @NotNull(message = "Escolha o tipo") DocumentKind kind,

            @NotBlank(message = "Informe o título") @Size(max = 200)
            String title,

            LocalDate documentDate) {}

    private final DocumentService service;

    DocumentController(DocumentService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Documentos ativos de uma licitação, com as versões", operationId = "listDocuments")
    List<DocumentView> list(@RequestParam UUID tenderId) {
        return service.list(tenderId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(
            summary = "Envia um documento",
            description = "Até 25 MB; PDF, PNG, JPG, Word, Excel ou ODF. O tipo é conferido pelo conteúdo.",
            operationId = "uploadDocument")
    DocumentView upload(
            @RequestParam UUID tenderId,
            @RequestParam DocumentKind kind,
            @RequestParam String title,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate documentDate,
            @RequestPart("file") MultipartFile file)
            throws IOException {
        return service.upload(tenderId, kind, title, documentDate, file.getOriginalFilename(), file.getBytes());
    }

    @PostMapping(path = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Envia uma versão nova do arquivo", operationId = "addDocumentVersion")
    DocumentView addVersion(@PathVariable UUID id, @RequestParam long version, @RequestPart("file") MultipartFile file)
            throws IOException {
        return service.addVersion(id, version, file.getOriginalFilename(), file.getBytes());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Altera tipo, título e data", operationId = "describeDocument")
    DocumentView describe(@PathVariable UUID id, @Valid @RequestBody DescribeRequest request) {
        return service.describe(id, request.version(), request.kind(), request.title(), request.documentDate());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Apaga o documento (os arquivos continuam guardados)", operationId = "deleteDocument")
    void delete(@PathVariable UUID id, @RequestParam long version) {
        service.delete(id, version);
    }

    @GetMapping("/{id}/versions/{number}/file")
    @Operation(summary = "Baixa o arquivo de uma versão", operationId = "downloadDocument")
    @ApiResponse(
            responseCode = "200",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary")))
    ResponseEntity<byte[]> download(@PathVariable UUID id, @PathVariable int number) {
        FileDownload file = service.download(id, number);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.content());
    }
}
