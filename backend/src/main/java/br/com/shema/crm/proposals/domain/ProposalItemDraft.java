package br.com.shema.crm.proposals.domain;

import java.math.BigDecimal;

/** Um item como veio da tela. Pode estar pela metade enquanto a pessoa digita. */
public record ProposalItemDraft(
        String itemNumber, String description, String unit, String brand, BigDecimal quantity, BigDecimal unitPrice) {}
