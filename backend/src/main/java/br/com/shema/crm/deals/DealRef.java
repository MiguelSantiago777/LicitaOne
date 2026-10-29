package br.com.shema.crm.deals;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Dados públicos de uma negociação, para outros módulos mostrarem e validarem sem conhecer a entidade.
 *
 * @param status EM_ANDAMENTO, GANHA ou PERDIDA
 */
public record DealRef(UUID id, String title, String status, UUID agencyId, UUID tenderId, BigDecimal value) {}
