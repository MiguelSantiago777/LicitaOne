package br.com.shema.crm.contracts.infrastructure;

import br.com.shema.crm.contracts.ContractFollowUp.ContractWithoutOrder;
import br.com.shema.crm.contracts.domain.ContractKind;
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

    /** Contratos abertos sem nenhum pedido, assinados até a data, do mais antigo para o mais novo. */
    public List<ContractWithoutOrder> withoutOrder(LocalDate signedOnOrBefore) {
        return jdbc.query(
                "select c.id, c.tender_id, c.kind, c.number, c.signed_at, c.value from contracts c"
                        + " where c.closed_at is null and c.signed_at <= :cutoff"
                        + " and not exists (select 1 from contract_orders o where o.contract_id = c.id)"
                        + " order by c.signed_at, c.created_at",
                new MapSqlParameterSource("cutoff", signedOnOrBefore),
                (rs, n) -> new ContractWithoutOrder(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tender_id", UUID.class),
                        ContractKind.valueOf(rs.getString("kind")).label(),
                        rs.getString("number"),
                        rs.getObject("signed_at", LocalDate.class),
                        rs.getBigDecimal("value").setScale(2)));
    }
}
