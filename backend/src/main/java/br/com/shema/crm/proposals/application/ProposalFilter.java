package br.com.shema.crm.proposals.application;

import br.com.shema.crm.proposals.domain.ProposalKind;
import java.time.LocalDate;

/**
 * Filtros da lista. {@code q} acha pelo número ("2026/0001" ou "12"), pelo nome do cliente ou pelo CNPJ. As canceladas
 * só aparecem com {@code includeCancelled}.
 */
public record ProposalFilter(String q, ProposalKind kind, LocalDate from, LocalDate to, boolean includeCancelled) {}
