package br.com.shema.crm.contracts.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Dados de um item, como vieram do formulário ou da planilha.
 *
 * @param id do item existente, ou null para um item novo
 */
public record ItemDraft(
        UUID id, String lot, String description, String unit, BigDecimal quantity, BigDecimal unitPrice) {}
