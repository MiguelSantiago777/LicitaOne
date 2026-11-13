/**
 * Propostas de preços (Fase 12, ADR 0022): número fixo por ano dado na criação, cliente digitado na hora, itens, condições
 * e o PDF sem assinatura, para quem baixa assinar no assinador. Não depende de licitações nem de órgãos; lê só o
 * cargo e o CPF de quem assina pela API pública de {@code identity}.
 */
@ApplicationModule(displayName = "Propostas de preços")
package br.com.shema.crm.proposals;

import org.springframework.modulith.ApplicationModule;
