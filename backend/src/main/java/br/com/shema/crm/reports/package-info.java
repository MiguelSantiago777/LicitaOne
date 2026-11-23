/**
 * Relatórios para o balanço (Fase 14, ADR 0024): pedidos recebidos, contratos assinados, licitações vencidas e
 * caronas por mês e ano, com as quebras por órgão, município/UF e tipo, na tela e em PDF. Só lê: os números vêm
 * somados pelas APIs públicas {@code TenderFigures}, {@code ContractFigures} e {@code CaronaFigures}.
 */
@ApplicationModule(displayName = "Relatórios")
package br.com.shema.crm.reports;

import org.springframework.modulith.ApplicationModule;
