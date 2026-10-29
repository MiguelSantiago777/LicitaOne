package br.com.shema.crm.shared.messaging;

import br.com.shema.crm.shared.metrics.CachedGauge;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.util.List;
import java.util.Properties;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Component;

/**
 * Mensagens paradas em cada DLQ (seção 7: tamanho de fila). Mais que zero pede atenção: o procedimento para
 * reprocessar está em docs/operacao/mensageria.md.
 */
@Component
class DeadLetterMetrics implements MeterBinder {

    private final AmqpAdmin admin;
    private final List<String> deadLetterQueues;
    private final Clock clock;

    DeadLetterMetrics(AmqpAdmin admin, List<Declarables> declarables, Clock clock) {
        this.admin = admin;
        this.deadLetterQueues = declarables.stream()
                .flatMap(d -> d.getDeclarablesByType(Queue.class).stream())
                .map(Queue::getName)
                .filter(name -> name.endsWith(Topology.DLQ_SUFFIX))
                .sorted()
                .toList();
        this.clock = clock;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (String dlq : deadLetterQueues) {
            CachedGauge messages = new CachedGauge(dlq, () -> count(dlq), clock);
            Gauge.builder("shema.messaging.dlq.messages", messages, CachedGauge::value)
                    .strongReference(true)
                    .description("Mensagens que esgotaram as tentativas e esperam reprocessamento")
                    .tag("queue", dlq.substring(0, dlq.length() - Topology.DLQ_SUFFIX.length()))
                    .register(registry);
        }
    }

    private long count(String queue) {
        Properties props = admin.getQueueProperties(queue);
        Object count = props == null ? null : props.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
        return count instanceof Number n ? n.longValue() : 0;
    }
}
