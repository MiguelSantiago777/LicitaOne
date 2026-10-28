package br.com.shema.crm.tenders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Dados públicos de uma licitação, para outros módulos mostrarem e validarem sem conhecer a entidade.
 *
 * @param identification UASG e/ou número do processo, já formatados ("UASG 160313 · PE 039/2026")
 * @param acceptsContracts se já foi vencida e ainda não foi encerrada (pode receber contratos)
 */
public record TenderRef(
        UUID id,
        UUID agencyId,
        String agencyName,
        String identification,
        String object,
        String materialType,
        BigDecimal totalValue,
        String status,
        LocalDate wonAt,
        UUID ownerId,
        boolean acceptsContracts) {}
