package br.com.shema.crm.imports.domain;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma importação de planilha: enviada → configurada (aba, cabeçalho, colunas) → na fila → concluída ou com falha.
 * O arquivo e o relatório ficam no {@code FileStorage}; aqui só as chaves e os números.
 */
@Entity
@Table(name = "import_jobs")
public class ImportJob {

    public enum Kind {
        CONTRACT_ITEMS
    }

    public enum Status {
        UPLOADED,
        CONFIGURED,
        QUEUED,
        DONE,
        FAILED
    }

    @Id
    private UUID id;

    @Version
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Kind kind;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_key", nullable = false, length = 255)
    private String fileKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "sheet_name", length = 100)
    private String sheetName;

    @Column(name = "header_row")
    private Integer headerRow;

    /** JSON {"DESCRIPTION": 2, "QUANTITY": 3, ...} com o índice da coluna (começa em 0). */
    @Column(columnDefinition = "text")
    private String mapping;

    @Column(name = "order_date")
    private LocalDate orderDate;

    @Column(name = "total_rows")
    private Integer totalRows;

    @Column(name = "imported_rows")
    private Integer importedRows;

    @Column(name = "error_rows")
    private Integer errorRows;

    @Column(name = "warning_rows")
    private Integer warningRows;

    @Column(name = "order_value", precision = 15, scale = 2)
    private BigDecimal orderValue;

    @Column(name = "report_key", length = 255)
    private String reportKey;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_by_id", nullable = false)
    private UUID createdById;

    @Column(name = "created_by_name", length = 120)
    private String createdByName;

    /** Perfis de quem enviou, para o consumidor conferir a permissão com o mesmo critério da requisição. */
    @Column(name = "created_by_roles", length = 100)
    private String createdByRoles;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "queued_at")
    private Instant queuedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected ImportJob() {}

    public static ImportJob upload(
            UUID contractId,
            String fileName,
            UUID createdById,
            String createdByName,
            String createdByRoles,
            Instant now) {
        ImportJob job = new ImportJob();
        job.id = UUID.randomUUID();
        job.kind = Kind.CONTRACT_ITEMS;
        job.contractId = contractId;
        job.fileName = fileName;
        job.fileKey = "imports/" + job.id + "/" + safeName(fileName);
        job.status = Status.UPLOADED;
        job.createdById = createdById;
        job.createdByName = createdByName;
        job.createdByRoles = createdByRoles;
        job.createdAt = now;
        return job;
    }

    /** Aba, cabeçalho e colunas escolhidos na prévia. Pode mudar até a confirmação. */
    public void configure(String sheetName, int headerRow, String mappingJson) {
        requireStatus(Status.UPLOADED, Status.CONFIGURED);
        this.sheetName = sheetName;
        this.headerRow = headerRow;
        this.mapping = mappingJson;
        this.status = Status.CONFIGURED;
    }

    public void queue(LocalDate orderDate, LocalDate today, Instant now) {
        requireStatus(Status.CONFIGURED);
        if (orderDate == null) {
            throw new DomainRuleViolation("DATE_REQUIRED", "Informe a data do pedido importado.");
        }
        if (orderDate.isAfter(today)) {
            throw new DomainRuleViolation("DATE_IN_FUTURE", "A data do pedido não pode ser no futuro.");
        }
        this.orderDate = orderDate;
        this.status = Status.QUEUED;
        this.queuedAt = now;
    }

    public void finish(
            int total, int imported, int errors, int warnings, BigDecimal orderValue, String reportKey, Instant now) {
        this.totalRows = total;
        this.importedRows = imported;
        this.errorRows = errors;
        this.warningRows = warnings;
        this.orderValue = orderValue;
        this.reportKey = reportKey;
        this.status = Status.DONE;
        this.finishedAt = now;
    }

    public void fail(String message, Instant now) {
        this.errorMessage = message.length() > 1000 ? message.substring(0, 1000) : message;
        this.status = Status.FAILED;
        this.finishedAt = now;
    }

    public boolean isFinished() {
        return status == Status.DONE || status == Status.FAILED;
    }

    public String reportKeyFor() {
        return "imports/" + id + "/relatorio.csv";
    }

    private void requireStatus(Status... allowed) {
        for (Status s : allowed) {
            if (status == s) {
                return;
            }
        }
        throw new DomainRuleViolation("IMPORT_STATUS", "Esta importação já foi confirmada.");
    }

    /** Nome do arquivo reduzido a caracteres seguros para a chave do armazenamento. */
    private static String safeName(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        String extension = lower.endsWith(".csv") ? ".csv" : ".xlsx";
        return "planilha" + extension;
    }

    public UUID getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public Kind getKind() {
        return kind;
    }

    public UUID getContractId() {
        return contractId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileKey() {
        return fileKey;
    }

    public Status getStatus() {
        return status;
    }

    public String getSheetName() {
        return sheetName;
    }

    public Integer getHeaderRow() {
        return headerRow;
    }

    public String getMapping() {
        return mapping;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public Integer getTotalRows() {
        return totalRows;
    }

    public Integer getImportedRows() {
        return importedRows;
    }

    public Integer getErrorRows() {
        return errorRows;
    }

    public Integer getWarningRows() {
        return warningRows;
    }

    public BigDecimal getOrderValue() {
        return orderValue;
    }

    public String getReportKey() {
        return reportKey;
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

    public String getCreatedByRoles() {
        return createdByRoles;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
