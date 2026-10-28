package br.com.shema.crm.shared.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

/** Blocos da topologia, para os módulos declararem as suas filas sempre do mesmo jeito. */
public final class Topology {

    public static final String DLQ_SUFFIX = ".dlq";

    private Topology() {}

    public static TopicExchange exchange(String name) {
        return new TopicExchange(name, true, false);
    }

    /**
     * Fila durável ligada ao exchange pela routing key, com a sua DLQ ({@code <fila>.dlq}): o consumidor rejeita a
     * mensagem depois das tentativas, e o RabbitMQ a move para a DLQ.
     */
    public static Declarables queue(String queueName, TopicExchange exchange, String routingKey) {
        Queue queue = QueueBuilder.durable(queueName)
                .deadLetterExchange(MessagingConfig.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(queueName)
                .build();
        Queue dlq = QueueBuilder.durable(queueName + DLQ_SUFFIX).build();
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(routingKey);
        Binding dlqBinding = BindingBuilder.bind(dlq)
                .to(new TopicExchange(MessagingConfig.DEAD_LETTER_EXCHANGE, true, false))
                .with(queueName);
        return new Declarables(exchange, queue, dlq, binding, dlqBinding);
    }
}
