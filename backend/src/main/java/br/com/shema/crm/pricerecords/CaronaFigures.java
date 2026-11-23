package br.com.shema.crm.pricerecords;

import br.com.shema.crm.shared.reporting.MonthlyFigure;
import java.util.List;

/** API pública de números das caronas para os relatórios (Fase 14, ADR 0024). Só leitura, somado no banco. */
public interface CaronaFigures {

    /** Caronas por mês da adesão e órgão aderente, com o valor da adesão. Sem tipo. */
    List<MonthlyFigure> caronasByMonth();
}
