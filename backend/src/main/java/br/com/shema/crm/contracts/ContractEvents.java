package br.com.shema.crm.contracts;

import br.com.shema.crm.shared.messaging.EventMetadata;
import br.com.shema.crm.shared.messaging.IntegrationEvent;
import br.com.shema.crm.shared.messaging.TracedEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Eventos de domínio publicados pelo módulo de contratos (ADR 0003). Saem também para o exchange
 * {@value #EXCHANGE}, para alertas e notificações (Fase 5).
 */
public final class ContractEvents {

    public static final String EXCHANGE = "shemacrm.contracts";

    private ContractEvents() {}

    @IntegrationEvent(exchange = EXCHANGE, type = "contract.signed")
    public record ContractSigned(
            EventMetadata metadata, UUID contractId, UUID tenderId, LocalDate signedAt, BigDecimal value)
            implements TracedEvent {}

    /** @param remaining saldo a pedir do contrato depois deste pedido */
    @IntegrationEvent(exchange = EXCHANGE, type = "contract.order-registered")
    public record OrderRegistered(
            EventMetadata metadata,
            UUID contractId,
            UUID tenderId,
            UUID orderId,
            LocalDate orderedAt,
            BigDecimal value,
            BigDecimal remaining)
            implements TracedEvent {}

    /** @param remaining saldo que sobrou sem pedido */
    @IntegrationEvent(exchange = EXCHANGE, type = "contract.closed")
    public record ContractClosed(
            EventMetadata metadata, UUID contractId, UUID tenderId, LocalDate closedAt, BigDecimal remaining)
            implements TracedEvent {}
}
