package br.com.shema.crm.contracts.infrastructure;

import br.com.shema.crm.contracts.ContractFigures;
import br.com.shema.crm.contracts.domain.ContractKind;
import br.com.shema.crm.shared.reporting.MonthlyFigure;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** {@link ContractFigures} com consultas agregadas. */
@Component
@Transactional(readOnly = true)
class ContractFiguresQuery implements ContractFigures {

    private final NamedParameterJdbcTemplate jdbc;

    ContractFiguresQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<MonthlyFigure> signedByMonth() {
        return jdbc.query("""
                select date_trunc('month', signed_at)::date as month, tender_id, kind,
                       count(*) as total, sum(value) as value
                  from contracts
                 group by 1, 2, 3
                """, new MapSqlParameterSource(), (rs, n) -> figure(rs));
    }

    @Override
    public List<MonthlyFigure> ordersByMonth() {
        return jdbc.query("""
                select date_trunc('month', o.ordered_at)::date as month, c.tender_id, c.kind,
                       count(*) as total, sum(o.value) as value
                  from contract_orders o
                  join contracts c on c.id = o.contract_id
                 group by 1, 2, 3
                """, new MapSqlParameterSource(), (rs, n) -> figure(rs));
    }

    private static MonthlyFigure figure(ResultSet rs) throws SQLException {
        String kind = rs.getString("kind");
        return new MonthlyFigure(
                YearMonth.from(rs.getObject("month", LocalDate.class)),
                rs.getObject("tender_id", UUID.class),
                null,
                kind,
                ContractKind.valueOf(kind).label(),
                rs.getLong("total"),
                rs.getBigDecimal("value").setScale(2));
    }
}
