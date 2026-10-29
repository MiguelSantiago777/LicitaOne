package br.com.shema.crm.notifications.application;

import br.com.shema.crm.notifications.domain.AlertRule;
import br.com.shema.crm.notifications.infrastructure.AlertMessaging;
import br.com.shema.crm.shared.messaging.MessageEnvelope;
import br.com.shema.crm.shared.messaging.ReliableConsumer;
import br.com.shema.crm.tasks.AlertTasks;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Conclui sozinha a tarefa do alerta quando o fato deixa de valer: o pedido registrado resolve "contrato sem pedido", e
 * o contrato assinado resolve "licitação vencida sem contrato". Escuta os eventos que os contratos já publicam.
 */
@Component
class ContractFactsConsumer {

    static final String CONSUMER = "alerts.contract-facts";

    private static final Logger log = LoggerFactory.getLogger(ContractFactsConsumer.class);

    /** Os dois campos que importam dos eventos de contrato; o resto do payload é ignorado. */
    record ContractFact(UUID contractId, UUID tenderId) {}

    private final ReliableConsumer consumer;
    private final AlertTasks tasks;

    ContractFactsConsumer(ReliableConsumer consumer, AlertTasks tasks) {
        this.consumer = consumer;
        this.tasks = tasks;
    }

    @RabbitListener(queues = AlertMessaging.CONTRACT_FACTS_QUEUE)
    void onMessage(Message message, Channel channel) throws IOException {
        consumer.consume(message, channel, CONSUMER, ContractFact.class, this::handle);
    }

    void handle(MessageEnvelope<ContractFact> envelope) {
        String key = switch (envelope.type()) {
            case AlertMessaging.ORDER_REGISTERED ->
                AlertRule.CONTRATO_SEM_PEDIDO.key(envelope.payload().contractId());
            case AlertMessaging.CONTRACT_SIGNED ->
                AlertRule.LICITACAO_SEM_CONTRATO.key(envelope.payload().tenderId());
            default -> null;
        };
        if (key != null && tasks.completeBySystem(key)) {
            log.info("Tarefa do alerta {} concluída: {}", key, envelope.type());
        }
    }
}
