package br.com.shema.crm.imports.application;

import br.com.shema.crm.contracts.ContractItemsImport;
import br.com.shema.crm.contracts.ContractItemsImport.ContractSummary;
import br.com.shema.crm.contracts.ContractItemsImport.RowOutcome;
import br.com.shema.crm.imports.ImportRequested;
import br.com.shema.crm.imports.domain.ImportJob;
import br.com.shema.crm.imports.domain.ItemField;
import br.com.shema.crm.imports.domain.ItemSheet;
import br.com.shema.crm.imports.domain.ItemSheet.ParsedRow;
import br.com.shema.crm.imports.domain.SheetData;
import br.com.shema.crm.imports.domain.SheetRow;
import br.com.shema.crm.imports.infrastructure.ImportJobRepository;
import br.com.shema.crm.imports.infrastructure.SpreadsheetReader;
import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.messaging.EventMetadata;
import br.com.shema.crm.shared.persistence.IdempotencyKeys;
import br.com.shema.crm.shared.security.CurrentUser;
import br.com.shema.crm.shared.storage.FileStorage;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Importação de itens e pedidos de um contrato (seção 5.2): upload → mapeamento de colunas → prévia com erros por
 * linha → confirmação (202, processada pelo consumidor do RabbitMQ) → status e relatório.
 */
@Service
@Transactional
public class ImportService {

    static final String CONFIRM_SCOPE = "import-confirm";
    static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    private static final int SAMPLE_ROWS = 3;

    public record ColumnInfo(int index, String letter, String header, String sample) {}

    public record SheetPreview(
            String name,
            Integer headerRow,
            List<ColumnInfo> columns,
            Map<ItemField, Integer> suggestedMapping,
            int rows) {}

    public record Upload(ImportJob job, ContractSummary contract, List<SheetPreview> sheets, String suggestedSheet) {}

    /** @param status CREATED, UPDATED, UNCHANGED, WARNING (da regra do contrato) ou ERROR (linha inválida) */
    public record PreviewRow(
            int rowNumber,
            String status,
            String lot,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal orderedQuantity,
            BigDecimal newOrderQuantity,
            String message) {}

    public record Preview(ImportJob job, List<PreviewRow> rows, Map<String, Long> totals, BigDecimal newOrderValue) {}

    private final ImportJobRepository jobs;
    private final ContractItemsImport contracts;
    private final SpreadsheetReader reader;
    private final FileStorage storage;
    private final IdempotencyKeys idempotency;
    private final ApplicationEventPublisher publisher;
    private final JsonMapper json;
    private final Clock clock;

    public ImportService(
            ImportJobRepository jobs,
            ContractItemsImport contracts,
            SpreadsheetReader reader,
            FileStorage storage,
            IdempotencyKeys idempotency,
            ApplicationEventPublisher publisher,
            JsonMapper json,
            Clock clock) {
        this.jobs = jobs;
        this.contracts = contracts;
        this.reader = reader;
        this.storage = storage;
        this.idempotency = idempotency;
        this.publisher = publisher;
        this.json = json;
        this.clock = clock;
    }

    /** Guarda a planilha e devolve as abas, com o cabeçalho e as colunas que conseguiu reconhecer. */
    public Upload upload(UUID contractId, String fileName, byte[] content) {
        CurrentUser user = CurrentUser.get();
        ContractSummary contract = editableContract(contractId, user);
        if (content.length == 0) {
            throw new DomainRuleViolation("FILE_EMPTY", "O arquivo está vazio.");
        }
        if (content.length > MAX_FILE_BYTES) {
            throw new DomainRuleViolation("FILE_TOO_LARGE", "A planilha passa de 10 MB.");
        }
        List<SheetData> sheets = reader.read(fileName, content);
        ImportJob job = ImportJob.upload(
                contractId, fileName, user.id(), user.name(), String.join(",", user.roles()), clock.instant());
        storage.put(job.getFileKey(), content);
        jobs.save(job);
        List<SheetPreview> previews =
                sheets.stream().map(ImportService::sheetPreview).toList();
        // A aba com todas as colunas obrigatórias reconhecidas e mais linhas de dados
        String suggested = previews.stream()
                .filter(sp -> sp.headerRow() != null
                        && ItemSheet.missingRequired(sp.suggestedMapping()).isEmpty())
                .max(java.util.Comparator.comparingInt(SheetPreview::rows))
                .map(SheetPreview::name)
                .orElse(previews.isEmpty() ? null : previews.getFirst().name());
        return new Upload(job, contract, previews, suggested);
    }

    /** Lê a aba com o mapeamento escolhido e mostra o que vai acontecer em cada linha. Não grava itens. */
    public Preview preview(UUID jobId, String sheetName, int headerRow, Map<ItemField, Integer> mapping) {
        CurrentUser user = CurrentUser.get();
        ImportJob job = get(jobId);
        editableContract(job.getContractId(), user);
        List<ItemField> missing = ItemSheet.missingRequired(mapping);
        if (!missing.isEmpty()) {
            throw new DomainRuleViolation(
                    "MAPPING_INCOMPLETE",
                    "Escolha a coluna de: "
                            + missing.stream().map(ItemField::label).collect(Collectors.joining(", ")) + ".");
        }
        SheetData sheet = sheet(job, sheetName);
        List<ParsedRow> parsed = ItemSheet.parse(sheet.rows(), headerRow, mapping);
        job.configure(sheet.name(), headerRow, json.writeValueAsString(mapping));

        Map<Integer, RowOutcome> outcomes = contracts.preview(job.getContractId(), ImportRows.valid(parsed)).stream()
                .collect(Collectors.toMap(RowOutcome::rowNumber, Function.identity()));
        List<PreviewRow> rows = new ArrayList<>();
        BigDecimal newOrderValue = BigDecimal.ZERO.setScale(2);
        for (ParsedRow row : parsed) {
            RowOutcome outcome = outcomes.get(row.rowNumber());
            if (outcome == null) {
                rows.add(previewRow(row, "ERROR", null, String.join(" ", row.errors())));
            } else {
                rows.add(previewRow(row, outcome.status().name(), outcome.newOrderQuantity(), outcome.message()));
                newOrderValue = newOrderValue.add(outcome.newOrderQuantity()
                        .multiply(row.unitPrice())
                        .setScale(2, java.math.RoundingMode.HALF_UP));
            }
        }
        Map<String, Long> totals = new LinkedHashMap<>();
        for (String status : List.of("CREATED", "UPDATED", "UNCHANGED", "WARNING", "ERROR")) {
            totals.put(
                    status, rows.stream().filter(r -> r.status().equals(status)).count());
        }
        return new Preview(job, rows, totals, newOrderValue);
    }

    /**
     * Confirma: o job vai para a fila e a resposta sai na hora (202). Com Idempotency-Key, confirmar de novo devolve o
     * mesmo job sem enfileirar outra vez.
     */
    public ImportJob confirm(UUID jobId, LocalDate orderDate, String idempotencyKey) {
        CurrentUser user = CurrentUser.get();
        ImportJob job = get(jobId);
        if (idempotency.find(CONFIRM_SCOPE, idempotencyKey, user.id()).isPresent()) {
            return job;
        }
        editableContract(job.getContractId(), user);
        job.queue(orderDate, LocalDate.now(clock), clock.instant());
        idempotency.remember(CONFIRM_SCOPE, idempotencyKey, user.id(), job.getId());
        publisher.publishEvent(new ImportRequested(EventMetadata.create(clock), job.getId()));
        jobs.flush();
        return job;
    }

    @Transactional(readOnly = true)
    public ImportJob get(UUID jobId) {
        return jobs.findById(jobId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Importação não encontrada."));
    }

    @Transactional(readOnly = true)
    public List<ImportJob> forContract(UUID contractId) {
        return jobs.findTop20ByContractIdOrderByCreatedAtDesc(contractId);
    }

    @Transactional(readOnly = true)
    public byte[] report(UUID jobId) {
        ImportJob job = get(jobId);
        if (job.getReportKey() == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "O relatório fica pronto quando a importação terminar.");
        }
        return storage.get(job.getReportKey());
    }

    private ContractSummary editableContract(UUID contractId, CurrentUser user) {
        ContractSummary contract = contracts
                .find(contractId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Contrato não encontrado."));
        user.requireCanEditOwnedBy(contract.ownerId());
        if (contract.closed()) {
            throw new DomainRuleViolation("CONTRACT_CLOSED", "Este contrato já foi encerrado.");
        }
        return contract;
    }

    private SheetData sheet(ImportJob job, String sheetName) {
        return reader.read(job.getFileName(), storage.get(job.getFileKey())).stream()
                .filter(s -> s.name().equals(sheetName))
                .findFirst()
                .orElseThrow(() -> new DomainRuleViolation("SHEET_NOT_FOUND", "Aba não encontrada: " + sheetName));
    }

    Map<ItemField, Integer> mappingOf(ImportJob job) {
        Map<String, Integer> raw = json.readValue(job.getMapping(), new TypeReference<Map<String, Integer>>() {});
        Map<ItemField, Integer> mapping = new EnumMap<>(ItemField.class);
        raw.forEach((k, v) -> mapping.put(ItemField.valueOf(k), v));
        return mapping;
    }

    private static PreviewRow previewRow(ParsedRow row, String status, BigDecimal newOrder, String message) {
        return new PreviewRow(
                row.rowNumber(),
                status,
                row.lot(),
                row.description(),
                row.quantity(),
                row.unitPrice(),
                row.orderedQuantity(),
                newOrder,
                message);
    }

    private static SheetPreview sheetPreview(SheetData sheet) {
        var header = ItemSheet.detectHeader(sheet.rows());
        if (header.isEmpty()) {
            List<ColumnInfo> columns = new ArrayList<>();
            int width =
                    sheet.rows().stream().mapToInt(r -> r.cells().size()).max().orElse(0);
            for (int c = 0; c < width; c++) {
                columns.add(new ColumnInfo(c, letter(c), null, sample(sheet.rows(), 0, c)));
            }
            return new SheetPreview(
                    sheet.name(), null, columns, Map.of(), sheet.rows().size());
        }
        int headerRow = header.get().rowNumber();
        SheetRow headerCells = sheet.rows().stream()
                .filter(r -> r.number() == headerRow)
                .findFirst()
                .orElseThrow();
        List<ColumnInfo> columns = new ArrayList<>();
        for (int c = 0; c < headerCells.cells().size(); c++) {
            columns.add(new ColumnInfo(c, letter(c), headerCells.cell(c), sample(sheet.rows(), headerRow, c)));
        }
        int dataRows =
                (int) sheet.rows().stream().filter(r -> r.number() > headerRow).count();
        return new SheetPreview(sheet.name(), headerRow, columns, header.get().mapping(), dataRows);
    }

    /** Os primeiros valores da coluna abaixo do cabeçalho, para o usuário reconhecer a coluna. */
    private static String sample(List<SheetRow> rows, int afterRow, int column) {
        return rows.stream()
                .filter(r -> r.number() > afterRow && r.cell(column) != null)
                .limit(SAMPLE_ROWS)
                .map(r -> r.cell(column))
                .collect(Collectors.joining(" · "));
    }

    /** Letra da coluna como no Excel (0 → A, 26 → AA). */
    static String letter(int index) {
        StringBuilder s = new StringBuilder();
        int n = index + 1;
        while (n > 0) {
            int rem = (n - 1) % 26;
            s.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return s.toString();
    }
}
