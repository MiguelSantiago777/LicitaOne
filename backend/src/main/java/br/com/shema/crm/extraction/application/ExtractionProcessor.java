package br.com.shema.crm.extraction.application;

import br.com.shema.crm.extraction.ExtractionRequested;
import br.com.shema.crm.extraction.application.DocumentReader.ReadRequest;
import br.com.shema.crm.extraction.application.DocumentReader.Reading;
import br.com.shema.crm.extraction.application.DocumentReader.ReadingFailed;
import br.com.shema.crm.extraction.domain.Extraction;
import br.com.shema.crm.extraction.domain.ReadingResult;
import br.com.shema.crm.extraction.infrastructure.ExtractionRepository;
import br.com.shema.crm.pricerecords.DocumentFiles;
import br.com.shema.crm.shared.messaging.ReliableConsumer;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Consumidor da fila de leitura. A chamada ao modelo pode levar mais de um minuto, então roda fora de transação: uma
 * transação curta marca o início, outra grava o resultado. A idempotência vem do estado da leitura (uma leitura
 * concluída não roda de novo).
 *
 * <p>Falha que a pessoa entende (documento recusado, IA fora do ar depois das tentativas do SDK) encerra a leitura com
 * a mensagem, sem nova tentativa: o botão "Ler com IA" continua lá. Só erro do nosso lado (banco, armazenamento) segue
 * o caminho de tentativas e DLQ.
 */
@Component
class ExtractionProcessor {

    static final String CONSUMER = "extraction.read-document";

    private static final Logger log = LoggerFactory.getLogger(ExtractionProcessor.class);

    private final ExtractionRepository extractions;
    private final ExtractionService service;
    private final DocumentFiles files;
    private final DocumentReader reader;
    private final ExtractionProperties properties;
    private final ExtractionMetrics metrics;
    private final ReliableConsumer consumer;
    private final TransactionTemplate tx;
    private final Clock clock;

    ExtractionProcessor(
            ExtractionRepository extractions,
            ExtractionService service,
            DocumentFiles files,
            DocumentReader reader,
            ExtractionProperties properties,
            ExtractionMetrics metrics,
            ReliableConsumer consumer,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.extractions = extractions;
        this.service = service;
        this.files = files;
        this.reader = reader;
        this.properties = properties;
        this.metrics = metrics;
        this.consumer = consumer;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @RabbitListener(queues = ExtractionRequested.QUEUE)
    void onMessage(Message message, Channel channel) throws IOException {
        consumer.consumeOutsideTransaction(
                message,
                channel,
                CONSUMER,
                ExtractionRequested.class,
                envelope -> process(envelope.payload().extractionId()));
    }

    void process(UUID extractionId) {
        Extraction started = tx.execute(status -> {
            Extraction e = extractions.findById(extractionId).orElse(null);
            if (e == null || !e.start(clock.instant())) {
                return null;
            }
            return e;
        });
        if (started == null) {
            log.info("Leitura {} não existe ou já terminou; nada a fazer", extractionId);
            return;
        }
        String fileName = files.current(started.getDocumentId())
                .map(DocumentFiles.DocumentFile::fileName)
                .orElse("documento.pdf");
        byte[] pdf = files.content(started.getDocumentId(), started.getDocumentVersion());

        Reading reading;
        try {
            reading = reader.read(new ReadRequest(pdf, fileName, started.getKind()));
        } catch (ReadingFailed e) {
            log.warn("Leitura {} falhou: {}", extractionId, e.getMessage(), e.getCause());
            fail(extractionId, e.getMessage(), e.model(), e.inputTokens(), e.outputTokens());
            return;
        } catch (RuntimeException e) {
            log.error("Leitura {} falhou de um jeito inesperado", extractionId, e);
            fail(
                    extractionId,
                    "Não deu para ler o documento agora. Tente de novo em alguns minutos.",
                    null,
                    null,
                    null);
            return;
        }

        ReadingResult result = ReadingResult.from(reading.raw(), properties.companyName());
        String resultJson = service.toJson(result);
        tx.executeWithoutResult(status -> extractions
                .findById(extractionId)
                .orElseThrow()
                .finish(resultJson, reading.model(), reading.inputTokens(), reading.outputTokens(), clock.instant()));
        metrics.finished(Extraction.Status.DONE, reading.inputTokens(), reading.outputTokens());
        log.info(
                "Leitura {} concluída por {}: {} itens, {} avisos, {} tokens de entrada e {} de saída",
                extractionId,
                reading.model(),
                result.items().size(),
                result.warnings().size(),
                reading.inputTokens(),
                reading.outputTokens());
    }

    private void fail(UUID extractionId, String message, String model, Long inputTokens, Long outputTokens) {
        tx.executeWithoutResult(status -> extractions
                .findById(extractionId)
                .orElseThrow()
                .fail(message, model, inputTokens, outputTokens, clock.instant()));
        metrics.finished(Extraction.Status.FAILED, inputTokens, outputTokens);
    }
}
