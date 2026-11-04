package br.com.shema.crm.extraction;

import br.com.shema.crm.shared.messaging.EventMetadata;
import br.com.shema.crm.shared.messaging.IntegrationEvent;
import br.com.shema.crm.shared.messaging.TracedEvent;
import java.util.UUID;

/** Alguém pediu para a IA ler um pedido (documento ou texto colado): o consumidor da fila faz a leitura (202). */
@IntegrationEvent(exchange = ExtractionRequested.EXCHANGE, type = OrderReadingRequested.TYPE)
public record OrderReadingRequested(EventMetadata metadata, UUID readingId) implements TracedEvent {

    public static final String TYPE = "order-reading.requested";
    public static final String QUEUE = "shemacrm.extraction.order-reading-requested";
}
