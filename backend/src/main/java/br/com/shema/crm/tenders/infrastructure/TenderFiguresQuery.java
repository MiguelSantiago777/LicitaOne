package br.com.shema.crm.tenders.infrastructure;

import br.com.shema.crm.shared.reporting.MonthlyFigure;
import br.com.shema.crm.tenders.TenderFigures;
import br.com.shema.crm.tenders.domain.Modality;
import br.com.shema.crm.tenders.domain.Sphere;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** {@link TenderFigures} com consultas agregadas: o relatório não carrega licitação por licitação. */
@Component
@Transactional(readOnly = true)
class TenderFiguresQuery implements TenderFigures {

    private final NamedParameterJdbcTemplate jdbc;

    TenderFiguresQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<MonthlyFigure> wonByMonth() {
        return jdbc.query("""
                select date_trunc('month', won_at)::date as month, agency_id, modality,
                       count(*) as total, sum(total_value) as value
                  from tenders
                 where won_at is not null and status not in ('EM_DISPUTA', 'PERDIDA')
                 group by 1, 2, 3
                """, new MapSqlParameterSource(), (rs, n) -> {
            String modality = rs.getString("modality");
            return new MonthlyFigure(
                    YearMonth.from(rs.getObject("month", LocalDate.class)),
                    null,
                    rs.getObject("agency_id", UUID.class),
                    modality,
                    Modality.valueOf(modality).label(),
                    rs.getLong("total"),
                    rs.getBigDecimal("value").setScale(2));
        });
    }

    @Override
    public Map<UUID, UUID> agencyOf(Collection<UUID> tenderIds) {
        Map<UUID, UUID> result = new HashMap<>();
        if (tenderIds.isEmpty()) {
            return result;
        }
        jdbc.query(
                "select id, agency_id from tenders where id in (:ids)",
                new MapSqlParameterSource("ids", tenderIds),
                rs -> {
                    result.put(rs.getObject("id", UUID.class), rs.getObject("agency_id", UUID.class));
                });
        return result;
    }

    @Override
    public Map<UUID, AgencyFacts> agencies(Collection<UUID> agencyIds) {
        Map<UUID, AgencyFacts> result = new HashMap<>();
        if (agencyIds.isEmpty()) {
            return result;
        }
        jdbc.query(
                "select id, name, city, state, sphere from agencies where id in (:ids)",
                new MapSqlParameterSource("ids", agencyIds),
                rs -> {
                    String sphere = rs.getString("sphere");
                    UUID id = rs.getObject("id", UUID.class);
                    result.put(
                            id,
                            new AgencyFacts(
                                    id,
                                    rs.getString("name"),
                                    rs.getString("city"),
                                    rs.getString("state"),
                                    sphere,
                                    Sphere.valueOf(sphere).label()));
                });
        return result;
    }
}
