package br.com.shema.crm.contracts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Eventos de domínio publicados pelo módulo de contratos (ADR 0003), para alertas e notificações. */
public final class ContractEvents {

    private ContractEvents() {}

    public record ContractSigned(UUID contractId, UUID tenderId, LocalDate signedAt, BigDecimal value) {}

    public record OrderRegistered(
            UUID contractId,
            UUID tenderId,
            UUID orderId,
            LocalDate orderedAt,
            BigDecimal value,
            BigDecimal remaining) {}

    public record ContractClosed(UUID contractId, UUID tenderId, LocalDate closedAt, BigDecimal remaining) {}
}
