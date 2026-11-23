package br.com.shema.crm.shared.reporting;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Um total do mês já somado no banco pelo módulo dono do dado (pedidos, contratos, licitações vencidas, caronas), para
 * os relatórios (Fase 14, ADR 0024). Vem agrupado por mês, licitação ou órgão e tipo; o relatório soma o resto.
 *
 * @param tenderId a licitação, quando o dado é dela (pedidos e contratos); o relatório descobre o órgão por ela
 * @param agencyId o órgão, quando o módulo já sabe (licitações vencidas e caronas)
 * @param kind código do tipo (EMPENHO, PREGAO_ELETRONICO...), ou nulo quando o número não tem tipo
 */
public record MonthlyFigure(
        YearMonth month, UUID tenderId, UUID agencyId, String kind, String kindLabel, long count, BigDecimal value) {}
