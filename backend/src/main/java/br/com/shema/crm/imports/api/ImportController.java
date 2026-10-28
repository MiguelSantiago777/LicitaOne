package br.com.shema.crm.imports.api;

import br.com.shema.crm.imports.application.ImportService;
import br.com.shema.crm.imports.application.ImportService.Preview;
import br.com.shema.crm.imports.application.ImportService.PreviewRow;
import br.com.shema.crm.imports.application.ImportService.SheetPreview;
import br.com.shema.crm.imports.application.ImportService.Upload;
import br.com.shema.crm.imports.domain.ImportJob;
import br.com.shema.crm.imports.domain.ItemField;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/imports")
@Tag(name = "imports", description = "Importação de planilhas")
class ImportController {

    record FieldOptionResponse(ItemField field, String label, boolean required) {}

    record ColumnResponse(int index, String letter, String header, String sample) {}

    record SheetResponse(
            String name,
            Integer headerRow,
            List<ColumnResponse> columns,
            Map<ItemField, Integer> suggestedMapping,
            int rows) {}

    record ImportJobResponse(
            UUID id,
            UUID contractId,
            String fileName,
            ImportJob.Status status,
            String sheetName,
            Integer headerRow,
            LocalDate orderDate,
            Integer totalRows,
            Integer importedRows,
            Integer errorRows,
            Integer warningRows,
            BigDecimal orderValue,
            boolean hasReport,
            String errorMessage,
            String createdByName,
            Instant createdAt,
            Instant finishedAt) {

        static ImportJobResponse from(ImportJob j) {
            return new ImportJobResponse(
                    j.getId(),
                    j.getContractId(),
                    j.getFileName(),
                    j.getStatus(),
                    j.getSheetName(),
                    j.getHeaderRow(),
                    j.getOrderDate(),
                    j.getTotalRows(),
                    j.getImportedRows(),
                    j.getErrorRows(),
                    j.getWarningRows(),
                    j.getOrderValue(),
                    j.getReportKey() != null,
                    j.getErrorMessage(),
                    j.getCreatedByName(),
                    j.getCreatedAt(),
                    j.getFinishedAt());
        }
    }

    record UploadResponse(
            ImportJobResponse job,
            String contractLabel,
            List<SheetResponse> sheets,
            String suggestedSheet,
            List<FieldOptionResponse> fields) {}

    record PreviewRequest(
            @NotBlank(message = "Escolha a aba") @Size(max = 100)
            String sheet,

            @NotNull(message = "Informe a linha do cabeçalho") @Positive
            Integer headerRow,

            @NotNull Map<ItemField, Integer> mapping) {}

    record PreviewRowResponse(
            int rowNumber,
            String status,
            String lot,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal orderedQuantity,
            BigDecimal newOrderQuantity,
            String message) {

        static PreviewRowResponse from(PreviewRow r) {
            return new PreviewRowResponse(
                    r.rowNumber(),
                    r.status(),
                    r.lot(),
                    r.description(),
                    r.quantity(),
                    r.unitPrice(),
                    r.orderedQuantity(),
                    r.newOrderQuantity(),
                    r.message());
        }
    }

    record PreviewResponse(
            ImportJobResponse job, List<PreviewRowResponse> rows, Map<String, Long> totals, BigDecimal newOrderValue) {}

    record ConfirmRequest(
            @NotNull(message = "Informe a data do pedido importado")
            LocalDate orderDate) {}

    private final ImportService service;

    ImportController(ImportService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(
            summary = "Envia uma planilha (.xlsx ou .csv) de itens e pedidos de um contrato",
            operationId = "uploadImport")
    UploadResponse upload(@RequestParam UUID contractId, @RequestPart("file") MultipartFile file) throws IOException {
        Upload upload = service.upload(contractId, file.getOriginalFilename(), file.getBytes());
        return new UploadResponse(
                ImportJobResponse.from(upload.job()),
                upload.contract().label(),
                upload.sheets().stream().map(ImportController::sheet).toList(),
                upload.suggestedSheet(),
                Arrays.stream(ItemField.values())
                        .map(f -> new FieldOptionResponse(f, f.label(), f.required()))
                        .toList());
    }

    @PostMapping("/{id}/preview")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Prévia linha a linha com o mapeamento de colunas escolhido", operationId = "previewImport")
    PreviewResponse preview(@PathVariable UUID id, @Valid @RequestBody PreviewRequest request) {
        Preview preview = service.preview(id, request.sheet(), request.headerRow(), request.mapping());
        return new PreviewResponse(
                ImportJobResponse.from(preview.job()),
                preview.rows().stream().map(PreviewRowResponse::from).toList(),
                preview.totals(),
                preview.newOrderValue());
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(
            summary = "Confirma a importação: processa em segundo plano (202) e o status é acompanhado por GET",
            operationId = "confirmImport")
    ResponseEntity<ImportJobResponse> confirm(
            @PathVariable UUID id,
            @RequestHeader(name = "Idempotency-Key", required = false) @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody ConfirmRequest request) {
        ImportJob job = service.confirm(id, request.orderDate(), idempotencyKey);
        return ResponseEntity.accepted()
                .header(HttpHeaders.LOCATION, "/api/v1/imports/" + job.getId())
                .body(ImportJobResponse.from(job));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Status de uma importação", operationId = "getImport")
    ImportJobResponse get(@PathVariable UUID id) {
        return ImportJobResponse.from(service.get(id));
    }

    @GetMapping
    @Operation(summary = "Importações recentes de um contrato", operationId = "contractImports")
    List<ImportJobResponse> forContract(@RequestParam UUID contractId) {
        return service.forContract(contractId).stream()
                .map(ImportJobResponse::from)
                .toList();
    }

    @GetMapping(value = "/{id}/report", produces = "text/csv")
    @Operation(summary = "Relatório final, linha a linha (CSV)", operationId = "importReport")
    ResponseEntity<byte[]> report(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("relatorio-importacao-" + id + ".csv")
                                .build()
                                .toString())
                .body(service.report(id));
    }

    private static SheetResponse sheet(SheetPreview s) {
        return new SheetResponse(
                s.name(),
                s.headerRow(),
                s.columns().stream()
                        .map(c -> new ColumnResponse(c.index(), c.letter(), c.header(), c.sample()))
                        .toList(),
                s.suggestedMapping(),
                s.rows());
    }
}
