package br.com.shema.crm.imports;

import br.com.shema.crm.shared.messaging.EventMetadata;
import br.com.shema.crm.shared.messaging.IntegrationEvent;
import br.com.shema.crm.shared.messaging.TracedEvent;
import java.util.UUID;

/** Uma importação foi confirmada: o consumidor da fila processa a planilha (resposta 202 na API). */
@IntegrationEvent(exchange = ImportRequested.EXCHANGE, type = ImportRequested.TYPE)
public record ImportRequested(EventMetadata metadata, UUID jobId) implements TracedEvent {

    public static final String EXCHANGE = "shemacrm.imports";
    public static final String TYPE = "import.requested";
    public static final String QUEUE = "shemacrm.imports.import-requested";
}
