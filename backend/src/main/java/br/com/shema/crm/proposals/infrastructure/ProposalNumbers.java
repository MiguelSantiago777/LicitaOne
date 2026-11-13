package br.com.shema.crm.proposals.infrastructure;

import br.com.shema.crm.proposals.domain.ProposalNumber;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Próximo número do ano. O upsert trava a linha do ano até o fim da transação de quem cria: duas criações ao mesmo
 * tempo saem com números seguidos, nunca iguais. Se a criação falhar, a transação desfaz e o número fica livre de novo;
 * depois de gravada, a proposta nunca é apagada, só cancelada.
 */
@Component
public class ProposalNumbers {

    private final JdbcClient jdbc;

    ProposalNumbers(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ProposalNumber next(int year) {
        Integer sequence =
                jdbc.sql("""
                        insert into proposal_counters (year, last_number) values (:year, 1)
                        on conflict (year) do update set last_number = proposal_counters.last_number + 1
                        returning last_number
                        """).param("year", year).query(Integer.class).single();
        return new ProposalNumber(year, sequence);
    }
}
