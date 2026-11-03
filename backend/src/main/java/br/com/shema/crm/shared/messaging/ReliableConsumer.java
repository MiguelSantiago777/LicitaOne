package br.com.shema.crm.shared.messaging;

import br.com.shema.crm.shared.web.CorrelationIdFilter;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumo confiável (ADR 0003): lê o envelope, propaga o correlationId, tenta de novo com backoff exponencial e só
 * confirma (ack manual) depois que o efeito foi gravado. A marca de "já processada" entra na mesma transação do
 * efeito, então uma mensagem repetida não faz nada. Esgotadas as tentativas, ou se o envelope for ilegível, a
 * mensagem é rejeitada sem voltar para a fila e cai na DLQ.
 */
@Component
public class ReliableConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReliableConsumer.class);

    private final ProcessedMessages processed;
    private final TransactionTemplate tx;
    private final RetryTemplate retry;
    private final JsonMapper json;

    public ReliableConsumer(
            ProcessedMessages processed, TransactionTemplate tx, RetryTemplate consumerRetry, JsonMapper json) {
        this.processed = processed;
        this.tx = tx;
        this.retry = consumerRetry;
        this.json = json;
    }

    public <T> void consume(
            Message message,
            Channel channel,
            String consumerName,
            Class<T> payloadType,
            Consumer<MessageEnvelope<T>> handler)
            throws IOException {
        run(
                message,
                channel,
                consumerName,
                payloadType,
                envelope -> tx.execute(status -> {
                    if (processed.markIfNew(envelope.eventId(), consumerName)) {
                        handler.accept(envelope);
                    } else {
                        log.info("Mensagem {} já processada por {}; ignorada", envelope.eventId(), consumerName);
                    }
                    return null;
                }));
    }

    /**
     * Como {@link #consume}, mas sem abrir transação nem marcar a mensagem: para trabalho demorado fora do banco (ex.:
     * chamada a um serviço externo), que não pode segurar uma conexão. O handler abre as próprias transações curtas e
     * precisa ser idempotente pelo estado do que processa.
     */
    public <T> void consumeOutsideTransaction(
            Message message,
            Channel channel,
            String consumerName,
            Class<T> payloadType,
            Consumer<MessageEnvelope<T>> handler)
            throws IOException {
        run(message, channel, consumerName, payloadType, handler);
    }

    private <T> void run(
            Message message,
            Channel channel,
            String consumerName,
            Class<T> payloadType,
            Consumer<MessageEnvelope<T>> handler)
            throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        MessageEnvelope<T> envelope;
        try {
            JavaType type = json.getTypeFactory().constructParametricType(MessageEnvelope.class, payloadType);
            envelope = json.readValue(message.getBody(), type);
        } catch (RuntimeException e) {
            log.error("Mensagem ilegível em {}; enviada para a DLQ", consumerName, e);
            channel.basicNack(tag, false, false);
            return;
        }
        if (envelope.correlationId() != null) {
            MDC.put(CorrelationIdFilter.MDC_KEY, envelope.correlationId());
        }
        try {
            retry.execute(() -> {
                handler.accept(envelope);
                return null;
            });
            channel.basicAck(tag, false);
        } catch (RetryException e) {
            log.error(
                    "Mensagem {} ({}) falhou em {} depois das tentativas; enviada para a DLQ",
                    envelope.eventId(),
                    envelope.type(),
                    consumerName,
                    e.getCause());
            channel.basicNack(tag, false, false);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }
}
