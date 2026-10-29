package br.com.shema.crm.notifications.application;

import br.com.shema.crm.identity.UserDirectory;
import br.com.shema.crm.notifications.AlertRaised;
import br.com.shema.crm.notifications.infrastructure.AlertLog;
import br.com.shema.crm.notifications.infrastructure.AlertMessaging;
import br.com.shema.crm.shared.messaging.ReliableConsumer;
import com.rabbitmq.client.Channel;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Aviso interno por e-mail (ADR 0006): vai para o responsável pela licitação ou, sem responsável ativo, para os
 * administradores. Nunca escreve para o órgão. Se o SMTP falhar, a mensagem é tentada de novo e depois vai para a DLQ,
 * de onde é reprocessada; a tarefa sai pela outra fila e não depende disto.
 */
@Component
class AlertNoticeConsumer {

    static final String CONSUMER = "alerts.notify-team";

    private static final Logger log = LoggerFactory.getLogger(AlertNoticeConsumer.class);

    private final ReliableConsumer consumer;
    private final UserDirectory users;
    private final ObjectProvider<JavaMailSender> mail;
    private final AlertLog alertLog;
    private final AlertProperties properties;
    private final MeterRegistry meters;
    private final Clock clock;

    AlertNoticeConsumer(
            ReliableConsumer consumer,
            UserDirectory users,
            ObjectProvider<JavaMailSender> mail,
            AlertLog alertLog,
            AlertProperties properties,
            MeterRegistry meters,
            Clock clock) {
        this.consumer = consumer;
        this.users = users;
        this.mail = mail;
        this.alertLog = alertLog;
        this.properties = properties;
        this.meters = meters;
        this.clock = clock;
    }

    @RabbitListener(queues = AlertMessaging.NOTIFY_TEAM_QUEUE)
    void onMessage(Message message, Channel channel) throws IOException {
        consumer.consume(message, channel, CONSUMER, AlertRaised.class, envelope -> notify(envelope.payload()));
    }

    /**
     * O e-mail sai dentro da transação que marca a mensagem como processada: se o SMTP recusar, nada é marcado e a
     * mensagem volta. O aviso só se repete se o banco falhar depois de o SMTP aceitar.
     */
    void notify(AlertRaised alert) {
        JavaMailSender sender = mail.getIfAvailable();
        if (!properties.mailEnabled() || sender == null) {
            log.info("Aviso por e-mail desligado; o alerta {} só gera a tarefa", alert.alertKey());
            meters.counter("shema.alerts.notices", "result", "disabled").increment();
            return;
        }
        List<String> to = users.activeEmail(alert.ownerId()).map(List::of).orElseGet(users::activeAdminEmails);
        if (to.isEmpty()) {
            log.warn("Alerta {} sem destinatário (sem responsável nem administrador ativo)", alert.alertKey());
            meters.counter("shema.alerts.notices", "result", "no-recipient").increment();
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(to.toArray(String[]::new));
        message.setSubject(alert.noticeSubject());
        message.setText(alert.noticeBody());
        sender.send(message);
        alertLog.notified(alert.alertId(), String.join(", ", to), clock.instant());
        meters.counter("shema.alerts.notices", "result", "sent").increment();
    }
}
