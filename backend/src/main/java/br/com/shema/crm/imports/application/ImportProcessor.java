package br.com.shema.crm.imports.application;

import br.com.shema.crm.contracts.ContractItemsImport;
import br.com.shema.crm.contracts.ContractItemsImport.ImportOutcome;
import br.com.shema.crm.contracts.ContractItemsImport.RowOutcome;
import br.com.shema.crm.imports.ImportRequested;
import br.com.shema.crm.imports.domain.ImportJob;
import br.com.shema.crm.imports.domain.ItemSheet;
import br.com.shema.crm.imports.domain.ItemSheet.ParsedRow;
import br.com.shema.crm.imports.domain.SheetData;
import br.com.shema.crm.imports.infrastructure.ImportJobRepository;
import br.com.shema.crm.imports.infrastructure.SpreadsheetReader;
import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.messaging.ReliableConsumer;
import br.com.shema.crm.shared.security.CurrentUser;
import br.com.shema.crm.shared.storage.FileStorage;
import br.com.shema.crm.shared.web.ApiException;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Consumidor da fila de importação. Grava itens e pedido numa transação própria: se a regra do contrato recusar (ex.:
 * pedido acima do saldo), nada do contrato muda e o job fica com falha e a mensagem do motivo, sem nova tentativa.
 * Erros inesperados seguem o caminho normal de tentativas e DLQ.
 */
@Component
class ImportProcessor {

    static final String CONSUMER = "imports.process-sheet";

    private static final Logger log = LoggerFactory.getLogger(ImportProcessor.class);

    private final ImportJobRepository jobs;
    private final ImportService service;
    private final ContractItemsImport contracts;
    private final SpreadsheetReader reader;
    private final FileStorage storage;
    private final ReliableConsumer consumer;
    private final TransactionTemplate newTransaction;
    private final Clock clock;

    ImportProcessor(
            ImportJobRepository jobs,
            ImportService service,
            ContractItemsImport contracts,
            SpreadsheetReader reader,
            FileStorage storage,
            ReliableConsumer consumer,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.jobs = jobs;
        this.service = service;
        this.contracts = contracts;
        this.reader = reader;
        this.storage = storage;
        this.consumer = consumer;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @RabbitListener(queues = ImportRequested.QUEUE)
    void onMessage(Message message, Channel channel) throws IOException {
        consumer.consume(
                message,
                channel,
                CONSUMER,
                ImportRequested.class,
                envelope -> process(envelope.payload().jobId()));
    }

    /** Roda dentro da transação do consumidor (a mesma da marca de mensagem processada). */
    void process(UUID jobId) {
        ImportJob job = jobs.findById(jobId).orElse(null);
        if (job == null || job.isFinished()) {
            log.info("Importação {} não existe ou já terminou; nada a fazer", jobId);
            return;
        }
        SheetData sheet = reader.read(job.getFileName(), storage.get(job.getFileKey())).stream()
                .filter(s -> s.name().equals(job.getSheetName()))
                .findFirst()
                .orElse(null);
        if (sheet == null) {
            job.fail("A aba \"" + job.getSheetName() + "\" não está mais no arquivo.", clock.instant());
            return;
        }
        List<ParsedRow> rows = ItemSheet.parse(sheet.rows(), job.getHeaderRow(), service.mappingOf(job));
        CurrentUser actor = new CurrentUser(
                job.getCreatedById(),
                job.getCreatedByName(),
                Arrays.asList(job.getCreatedByRoles().split(",")));
        ImportOutcome outcome;
        try {
            outcome = newTransaction.execute(
                    status -> contracts.apply(job.getContractId(), ImportRows.valid(rows), job.getOrderDate(), actor));
        } catch (DomainRuleViolation | ApiException e) {
            log.warn("Importação {} recusada: {}", jobId, e.getMessage());
            job.fail(e.getMessage(), clock.instant());
            return;
        }
        Map<Integer, RowOutcome> byRow =
                outcome.rows().stream().collect(Collectors.toMap(RowOutcome::rowNumber, Function.identity()));
        String reportKey = job.reportKeyFor();
        storage.put(reportKey, ImportReport.csv(rows, byRow));
        int errors = (int) rows.stream().filter(r -> !r.valid()).count();
        int warnings = (int) outcome.rows().stream()
                .filter(r -> r.status() == ContractItemsImport.RowStatus.WARNING)
                .count();
        job.finish(
                rows.size(),
                rows.size() - errors - warnings,
                errors,
                warnings,
                outcome.orderValue(),
                reportKey,
                clock.instant());
        log.info("Importação {} concluída: {} linhas, {} com erro, {} com aviso", jobId, rows.size(), errors, warnings);
    }
}
