package br.com.shema.crm.extraction.application;

import br.com.shema.crm.contracts.ContractOrderContext;
import br.com.shema.crm.contracts.ContractOrderContext.OrderContext;
import br.com.shema.crm.extraction.OrderReadingRequested;
import br.com.shema.crm.extraction.application.DocumentReader.OrderRead;
import br.com.shema.crm.extraction.application.DocumentReader.OrderReadRequest;
import br.com.shema.crm.extraction.application.DocumentReader.ReadingFailed;
import br.com.shema.crm.extraction.domain.Extraction;
import br.com.shema.crm.extraction.domain.OrderReading;
import br.com.shema.crm.extraction.domain.OrderReadingResult;
import br.com.shema.crm.extraction.infrastructure.OrderReadingRepository;
import br.com.shema.crm.pricerecords.DocumentFiles;
import br.com.shema.crm.pricerecords.DocumentFiles.DocumentFile;
import br.com.shema.crm.shared.messaging.ReliableConsumer;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Consumidor da fila de leitura de pedido, no mesmo modelo do {@link ExtractionProcessor}: a chamada ao modelo roda
 * fora de transação, e a idempotência vem do estado da leitura. O texto colado nunca vai para os logs.
 *
 * <p>Os itens e o saldo do contrato são lidos na hora da leitura, então a conferência usa o saldo atual.
 */
@Component
class OrderReadingProcessor {

    static final String CONSUMER = "extraction.read-order";

    private static final Logger log = LoggerFactory.getLogger(OrderReadingProcessor.class);

    private final OrderReadingRepository readings;
    private final OrderReadingService service;
    private final ContractOrderContext contracts;
    private final DocumentFiles files;
    private final DocumentReader reader;
    private final ExtractionMetrics metrics;
    private final ReliableConsumer consumer;
    private final TransactionTemplate tx;
    private final Clock clock;

    OrderReadingProcessor(
            OrderReadingRepository readings,
            OrderReadingService service,
            ContractOrderContext contracts,
            DocumentFiles files,
            DocumentReader reader,
            ExtractionMetrics metrics,
            ReliableConsumer consumer,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.readings = readings;
        this.service = service;
        this.contracts = contracts;
        this.files = files;
        this.reader = reader;
        this.metrics = metrics;
        this.consumer = consumer;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @RabbitListener(queues = OrderReadingRequested.QUEUE)
    void onMessage(Message message, Channel channel) throws IOException {
        consumer.consumeOutsideTransaction(
                message,
                channel,
                CONSUMER,
                OrderReadingRequested.class,
                envelope -> process(envelope.payload().readingId()));
    }

    void process(UUID readingId) {
        OrderReading started = tx.execute(status -> {
            OrderReading r = readings.findById(readingId).orElse(null);
            if (r == null || !r.start(clock.instant())) {
                return null;
            }
            return r;
        });
        if (started == null) {
            log.info("Leitura de pedido {} não existe ou já terminou; nada a fazer", readingId);
            return;
        }
        OrderContext contract = contracts.find(started.getContractId()).orElse(null);
        if (contract == null) {
            fail(readingId, "O contrato não existe mais.", null, null, null);
            return;
        }
        OrderReadRequest request = request(started, contract);
        if (request == null) {
            fail(readingId, "O documento foi apagado antes da leitura.", null, null, null);
            return;
        }

        OrderRead read;
        try {
            read = reader.readOrder(request);
        } catch (ReadingFailed e) {
            log.warn("Leitura de pedido {} falhou: {}", readingId, e.getMessage(), e.getCause());
            fail(readingId, e.getMessage(), e.model(), e.inputTokens(), e.outputTokens());
            return;
        } catch (RuntimeException e) {
            log.error("Leitura de pedido {} falhou de um jeito inesperado", readingId, e);
            fail(readingId, "Não deu para ler o pedido agora. Tente de novo em alguns minutos.", null, null, null);
            return;
        }

        OrderReadingResult result =
                OrderReadingResult.from(read.raw(), OrderReadingService.forCheck(contract), LocalDate.now(clock));
        String resultJson = service.toJson(result);
        tx.executeWithoutResult(status -> readings.findById(readingId)
                .orElseThrow()
                .finish(resultJson, read.model(), read.inputTokens(), read.outputTokens(), clock.instant()));
        metrics.finished("PEDIDO", Extraction.Status.DONE, read.inputTokens(), read.outputTokens());
        log.info(
                "Leitura de pedido {} concluída por {}: {} linhas, {} avisos, {} tokens de entrada e {} de saída",
                readingId,
                read.model(),
                result.lines().size(),
                result.warnings().size(),
                read.inputTokens(),
                read.outputTokens());
    }

    private OrderReadRequest request(OrderReading reading, OrderContext contract) {
        String description = OrderReadingService.describe(contract);
        if (reading.getSource() == OrderReading.Source.TEXT) {
            return new OrderReadRequest(null, null, null, reading.getSourceText(), description);
        }
        DocumentFile file = files.current(reading.getDocumentId()).orElse(null);
        if (file == null) {
            return null;
        }
        byte[] content = files.content(reading.getDocumentId(), reading.getDocumentVersion());
        return new OrderReadRequest(content, file.contentType(), file.fileName(), null, description);
    }

    private void fail(UUID readingId, String message, String model, Long inputTokens, Long outputTokens) {
        tx.executeWithoutResult(status -> readings.findById(readingId)
                .orElseThrow()
                .fail(message, model, inputTokens, outputTokens, clock.instant()));
        metrics.finished("PEDIDO", Extraction.Status.FAILED, inputTokens, outputTokens);
    }
}
