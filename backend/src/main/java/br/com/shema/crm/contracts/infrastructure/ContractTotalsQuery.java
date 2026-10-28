package br.com.shema.crm.contracts.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Totais de contratos e pedidos calculados no banco, para listas e painel não carregarem os agregados. */
@Component
public class ContractTotalsQuery {

    /** Pedidos somados por contrato, juntados aos contratos. */
    private static final String ORDERS_BY_CONTRACT = """
            left join (select contract_id, sum(value) as ordered, max(ordered_at) as last_order
                       from contract_orders group by contract_id) o on o.contract_id = c.id
            """;

    public record TenderTotals(
            UUID tenderId,
            long contracts,
            long openContracts,
            BigDecimal value,
            BigDecimal ordered,
            LocalDate lastOrderAt) {

        public BigDecimal remaining() {
            return value.subtract(ordered);
        }
    }

    public record OpenTotals(long contracts, BigDecimal value, BigDecimal ordered) {

        public BigDecimal remaining() {
            return value.subtract(ordered);
        }
    }

    private final NamedParameterJdbcTemplate jdbc;

    public ContractTotalsQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TenderTotals> byTender(Collection<UUID> tenderIds) {
        if (tenderIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(
                "select c.tender_id, count(*) as contracts, count(*) filter (where c.closed_at is null) as open_contracts,"
                        + " sum(c.value) as value, coalesce(sum(o.ordered), 0) as ordered,"
                        + " max(o.last_order) as last_order"
                        + " from contracts c " + ORDERS_BY_CONTRACT
                        + " where c.tender_id in (:ids) group by c.tender_id",
                new MapSqlParameterSource("ids", tenderIds),
                (rs, n) -> new TenderTotals(
                        rs.getObject("tender_id", UUID.class),
                        rs.getLong("contracts"),
                        rs.getLong("open_contracts"),
                        rs.getBigDecimal("value").setScale(2),
                        rs.getBigDecimal("ordered").setScale(2),
                        rs.getObject("last_order", LocalDate.class)));
    }

    /** Contratos ainda não encerrados: quantos são, quanto valem e quanto já foi pedido. */
    public OpenTotals open() {
        return jdbc.queryForObject(
                "select count(*) as contracts, coalesce(sum(c.value), 0) as value,"
                        + " coalesce(sum(o.ordered), 0) as ordered"
                        + " from contracts c " + ORDERS_BY_CONTRACT
                        + " where c.closed_at is null",
                new MapSqlParameterSource(),
                (rs, n) -> new OpenTotals(
                        rs.getLong("contracts"),
                        rs.getBigDecimal("value").setScale(2),
                        rs.getBigDecimal("ordered").setScale(2)));
    }
}
