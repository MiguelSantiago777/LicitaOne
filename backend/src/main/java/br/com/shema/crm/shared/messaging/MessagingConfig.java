package br.com.shema.crm.shared.messaging;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mensageria (ADR 0003): o outbox do Spring Modulith grava o evento na transação e, depois do commit, publica no
 * RabbitMQ os eventos marcados com {@link IntegrationEvent}, dentro de um {@link MessageEnvelope}.
 */
@Configuration(proxyBeanMethods = false)
public class MessagingConfig {

    /** Exchange para onde vão as mensagens que esgotaram as tentativas; cada fila tem a sua DLQ ligada aqui. */
    public static final String DEAD_LETTER_EXCHANGE = "shemacrm.dlx";

    /**
     * O Modulith cria esta configuração bem cedo, antes do Jackson; por isso o {@link JsonMapper} é buscado só na
     * hora de mapear o primeiro evento.
     */
    @Bean
    static EventExternalizationConfiguration eventExternalization(ObjectProvider<JsonMapper> json) {
        return EventExternalizationConfiguration.externalizing()
                .selectByAnnotation(IntegrationEvent.class)
                .mapping(event -> envelope(event, json.getObject()))
                .routeAll(event -> {
                    IntegrationEvent spec = spec(event);
                    return RoutingTarget.forTarget(spec.exchange()).andKey(spec.type());
                })
                .build();
    }

    /**
     * JSON só na publicação (o envelope sai como JSON). Os consumidores recebem a mensagem crua e o
     * {@link ReliableConsumer} lê o envelope: assim uma mensagem ilegível chega até ele e vai para a DLQ com log.
     */
    @Bean
    RabbitTemplateCustomizer jsonOnPublish(JsonMapper json) {
        return template -> template.setMessageConverter(new JacksonJsonMessageConverter(json));
    }

    @Bean
    Declarables deadLetterExchange() {
        return new Declarables(new TopicExchange(DEAD_LETTER_EXCHANGE, true, false));
    }

    /** 4 tentativas no total (1 s, 2 s, 4 s entre elas); depois a mensagem vai para a DLQ. */
    @Bean
    RetryTemplate consumerRetry() {
        return new RetryTemplate(RetryPolicy.builder()
                .maxRetries(3)
                .delay(Duration.ofSeconds(1))
                .multiplier(2)
                .maxDelay(Duration.ofSeconds(10))
                .build());
    }

    static IntegrationEvent spec(Object event) {
        IntegrationEvent spec = event.getClass().getAnnotation(IntegrationEvent.class);
        if (spec == null) {
            throw new IllegalStateException(event.getClass() + " não é um @IntegrationEvent");
        }
        return spec;
    }

    private static MessageEnvelope<Map<String, Object>> envelope(Object event, JsonMapper json) {
        IntegrationEvent spec = spec(event);
        EventMetadata meta = metadata(event);
        Map<String, Object> payload =
                new HashMap<>(json.convertValue(event, new TypeReference<Map<String, Object>>() {}));
        payload.remove("metadata");
        return new MessageEnvelope<>(
                meta.eventId(), spec.type(), spec.version(), meta.occurredAt(), meta.correlationId(), payload);
    }

    private static EventMetadata metadata(Object event) {
        if (!(event instanceof TracedEvent traced) || traced.metadata() == null) {
            throw new IllegalStateException(event.getClass() + " precisa implementar TracedEvent com metadata");
        }
        return traced.metadata();
    }
}
