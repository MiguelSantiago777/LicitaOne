package br.com.shema.crm.shared.messaging;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca um evento de domínio que também sai para o RabbitMQ (ADR 0003). Depois do commit, o outbox do Spring
 * Modulith publica o evento no exchange, com a routing key {@link #type()}, dentro de um {@link MessageEnvelope}.
 * O evento precisa ter um componente {@link EventMetadata} {@code metadata}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface IntegrationEvent {

    /** Exchange topic do contexto, ex.: {@code shemacrm.contracts}. */
    String exchange();

    /** Tipo do evento e routing key, ex.: {@code contract.signed}. */
    String type();

    /** Versão do formato do payload; sobe quando um campo muda de sentido ou some. */
    int version() default 1;
}
