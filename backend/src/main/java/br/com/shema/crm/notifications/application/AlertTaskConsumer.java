package br.com.shema.crm.notifications.application;

import br.com.shema.crm.notifications.AlertRaised;
import br.com.shema.crm.notifications.infrastructure.AlertLog;
import br.com.shema.crm.notifications.infrastructure.AlertMessaging;
import br.com.shema.crm.shared.TimeConfig;
import br.com.shema.crm.shared.messaging.ReliableConsumer;
import br.com.shema.crm.tasks.AlertTasks;
import br.com.shema.crm.tasks.AlertTasks.AlertTask;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Cria a tarefa do alerta, com o rascunho ao órgão. Três barreiras contra tarefa duplicada: a chave nova no job, a
 * mensagem já processada e a chave única na tarefa.
 */
@Component
class AlertTaskConsumer {

    static final String CONSUMER = "alerts.create-task";

    /** Vencimento da tarefa: fim do expediente do dia do alerta. */
    private static final LocalTime DUE_TIME = LocalTime.of(18, 0);

    private final ReliableConsumer consumer;
    private final AlertTasks tasks;
    private final AlertLog alertLog;
    private final Clock clock;

    AlertTaskConsumer(ReliableConsumer consumer, AlertTasks tasks, AlertLog alertLog, Clock clock) {
        this.consumer = consumer;
        this.tasks = tasks;
        this.alertLog = alertLog;
        this.clock = clock;
    }

    @RabbitListener(queues = AlertMessaging.CREATE_TASK_QUEUE)
    void onMessage(Message message, Channel channel) throws IOException {
        consumer.consume(message, channel, CONSUMER, AlertRaised.class, envelope -> open(envelope.payload()));
    }

    void open(AlertRaised alert) {
        UUID taskId = tasks.open(new AlertTask(
                alert.alertKey(),
                alert.title(),
                alert.notes(),
                alert.dueOn().atTime(DUE_TIME).atZone(TimeConfig.BUSINESS_ZONE).toOffsetDateTime(),
                alert.ownerId(),
                alert.tenderId(),
                alert.draft() == null
                        ? null
                        : new AlertTasks.Draft(
                                alert.draft().to(),
                                alert.draft().subject(),
                                alert.draft().body())));
        alertLog.taskOpened(alert.alertId(), taskId, clock.instant());
    }
}
