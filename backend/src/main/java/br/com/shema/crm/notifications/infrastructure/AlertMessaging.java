package br.com.shema.crm.notifications.infrastructure;

import br.com.shema.crm.contracts.ContractEvents;
import br.com.shema.crm.notifications.AlertRaised;
import br.com.shema.crm.shared.messaging.Topology;
import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Filas dos alertas, cada uma com a sua DLQ (ADR 0015). A tarefa e o aviso interno são consumidores separados, para
 * uma falha no e-mail não segurar a tarefa. A terceira fila escuta os contratos para concluir sozinha a tarefa cujo
 * fato deixou de valer.
 */
@Configuration(proxyBeanMethods = false)
public class AlertMessaging {

    public static final String CREATE_TASK_QUEUE = "shemacrm.alerts.create-task";
    public static final String NOTIFY_TEAM_QUEUE = "shemacrm.alerts.notify-team";
    public static final String CONTRACT_FACTS_QUEUE = "shemacrm.alerts.contract-facts";

    public static final String CONTRACT_SIGNED = "contract.signed";
    public static final String ORDER_REGISTERED = "contract.order-registered";

    @Bean
    Declarables alertTaskQueue() {
        return Topology.queue(CREATE_TASK_QUEUE, Topology.exchange(AlertRaised.EXCHANGE), AlertRaised.TYPE);
    }

    @Bean
    Declarables alertNoticeQueue() {
        return Topology.queue(NOTIFY_TEAM_QUEUE, Topology.exchange(AlertRaised.EXCHANGE), AlertRaised.TYPE);
    }

    @Bean
    Declarables alertContractFactsQueue() {
        TopicExchange contracts = Topology.exchange(ContractEvents.EXCHANGE);
        Declarables declarables = Topology.queue(CONTRACT_FACTS_QUEUE, contracts, CONTRACT_SIGNED);
        Queue queue = declarables.getDeclarablesByType(Queue.class).stream()
                .filter(q -> q.getName().equals(CONTRACT_FACTS_QUEUE))
                .findFirst()
                .orElseThrow();
        List<Declarable> all = new ArrayList<>(declarables.getDeclarables());
        all.add(BindingBuilder.bind(queue).to(contracts).with(ORDER_REGISTERED));
        return new Declarables(all);
    }
}
