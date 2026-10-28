package br.com.shema.crm.shared.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Formato de toda mensagem no RabbitMQ: cabeçalho versionado e o evento em {@code payload}.
 *
 * <pre>{"eventId": "...", "type": "contract.signed", "version": 1, "occurredAt": "...", "correlationId": "...",
 *  "payload": {...}}</pre>
 */
public record MessageEnvelope<T>(
        UUID eventId, String type, int version, Instant occurredAt, String correlationId, T payload) {}
