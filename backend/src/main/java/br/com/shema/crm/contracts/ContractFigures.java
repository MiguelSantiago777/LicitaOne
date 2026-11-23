package br.com.shema.crm.contracts;

import br.com.shema.crm.shared.reporting.MonthlyFigure;
import java.util.List;

/**
 * API pública de números dos contratos para os relatórios (Fase 14, ADR 0024). Só leitura, somado no banco. O tipo é
 * o do contrato (contrato, empenho ou dispensa); o órgão vem pela licitação.
 */
public interface ContractFigures {

    /** Contratos, empenhos e dispensas por mês da assinatura, licitação e tipo, com o valor do contrato. */
    List<MonthlyFigure> signedByMonth();

    /** Pedidos por mês do pedido, licitação e tipo do contrato, com o valor do pedido. */
    List<MonthlyFigure> ordersByMonth();
}
