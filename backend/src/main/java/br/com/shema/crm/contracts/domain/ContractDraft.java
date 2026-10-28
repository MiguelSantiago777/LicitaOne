package br.com.shema.crm.contracts.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dados editáveis do contrato.
 *
 * @param notes campo livre do que foi pedido e do que falta pedir, como a equipe anota hoje na planilha
 */
public record ContractDraft(
        ContractKind kind, String number, LocalDate signedAt, LocalDate validUntil, BigDecimal value, String notes) {}
