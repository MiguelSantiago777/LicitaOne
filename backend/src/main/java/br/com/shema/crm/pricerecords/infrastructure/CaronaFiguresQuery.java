package br.com.shema.crm.pricerecords.infrastructure;

import br.com.shema.crm.pricerecords.CaronaFigures;
import br.com.shema.crm.shared.reporting.MonthlyFigure;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** {@link CaronaFigures} com uma consulta agregada. */
@Component
@Transactional(readOnly = true)
class CaronaFiguresQuery implements CaronaFigures {

    private final NamedParameterJdbcTemplate jdbc;

    CaronaFiguresQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<MonthlyFigure> caronasByMonth() {
        return jdbc.query(
                """
                select date_trunc('month', adhered_at)::date as month, agency_id,
                       count(*) as total, sum(value) as value
                  from caronas
                 group by 1, 2
                """,
                new MapSqlParameterSource(),
                (rs, n) -> new MonthlyFigure(
                        YearMonth.from(rs.getObject("month", LocalDate.class)),
                        null,
                        rs.getObject("agency_id", UUID.class),
                        null,
                        null,
                        rs.getLong("total"),
                        rs.getBigDecimal("value").setScale(2)));
    }
}
