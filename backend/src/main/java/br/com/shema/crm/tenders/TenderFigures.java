package br.com.shema.crm.tenders;

import br.com.shema.crm.shared.reporting.MonthlyFigure;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** API pública de números das licitações para os relatórios (Fase 14, ADR 0024). Só leitura, somado no banco. */
public interface TenderFigures {

    /** Órgão com o que os relatórios usam para dividir e filtrar. */
    record AgencyFacts(UUID id, String name, String city, String state, String sphere, String sphereLabel) {}

    /**
     * Licitações vencidas por mês da vitória, órgão e modalidade, com o valor total da licitação. Conta a que tem data
     * de vitória e não voltou para em disputa nem perdida.
     */
    List<MonthlyFigure> wonByMonth();

    /** O órgão de cada licitação, em lote. */
    Map<UUID, UUID> agencyOf(Collection<UUID> tenderIds);

    /** Os órgãos, em lote. */
    Map<UUID, AgencyFacts> agencies(Collection<UUID> agencyIds);
}
