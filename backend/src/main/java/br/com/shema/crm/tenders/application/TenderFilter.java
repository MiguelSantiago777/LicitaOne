package br.com.shema.crm.tenders.application;

import br.com.shema.crm.tenders.domain.TenderStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Filtros da listagem. Todos opcionais.
 *
 * @param from início do período, aplicado à data relevante do estado (vitória, assinatura, encerramento)
 * @param signedBefore só contratos assinados até esta data (usado pelo alerta de "sem pedido há 30 dias")
 */
public record TenderFilter(
        Set<TenderStatus> statuses,
        String q,
        UUID agencyId,
        String city,
        String state,
        String materialType,
        BigDecimal minValue,
        BigDecimal maxValue,
        LocalDate from,
        LocalDate to,
        UUID ownerId,
        LocalDate signedBefore) {}
