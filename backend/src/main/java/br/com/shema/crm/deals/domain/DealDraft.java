package br.com.shema.crm.deals.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Conteúdo editável de uma negociação. A etapa muda só pelo "mover". */
public record DealDraft(
        String title, UUID agencyId, UUID tenderId, BigDecimal value, int priority, UUID ownerId, String notes) {}
