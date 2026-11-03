package br.com.shema.crm.extraction;

import br.com.shema.crm.shared.messaging.EventMetadata;
import br.com.shema.crm.shared.messaging.IntegrationEvent;
import br.com.shema.crm.shared.messaging.TracedEvent;
import java.util.UUID;

/** Alguém pediu para a IA ler um documento: o consumidor da fila faz a leitura (resposta 202 na API). */
@IntegrationEvent(exchange = ExtractionRequested.EXCHANGE, type = ExtractionRequested.TYPE)
public record ExtractionRequested(EventMetadata metadata, UUID extractionId) implements TracedEvent {

    public static final String EXCHANGE = "shemacrm.extraction";
    public static final String TYPE = "extraction.requested";
    public static final String QUEUE = "shemacrm.extraction.extraction-requested";
}
