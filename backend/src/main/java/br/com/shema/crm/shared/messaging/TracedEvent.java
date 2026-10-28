package br.com.shema.crm.shared.messaging;

/** Evento que carrega a própria identidade ({@link EventMetadata}); obrigatório para um {@link IntegrationEvent}. */
public interface TracedEvent {

    EventMetadata metadata();
}
