package br.com.shema.crm.shared.messaging;

import br.com.shema.crm.shared.web.CorrelationIdFilter;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Identidade de um evento, gravada junto com ele no outbox. Precisa nascer na publicação (e não na externalização):
 * se o evento for reenviado depois de uma queda, sai com o mesmo eventId, e o consumidor reconhece a repetição.
 */
public record EventMetadata(UUID eventId, Instant occurredAt, String correlationId) {

    /** Metadados de um evento novo, com o correlationId da requisição (ou da mensagem) em andamento. */
    public static EventMetadata create(Clock clock) {
        return new EventMetadata(UUID.randomUUID(), clock.instant(), MDC.get(CorrelationIdFilter.MDC_KEY));
    }
}
