package br.com.shema.crm.reports.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * O relatório de um número num ano, com os filtros aplicados. Tudo que a tela e o PDF mostram já vem calculado aqui,
 * para os dois baterem.
 *
 * @param elapsedMonths meses do ano que já passaram (12 em ano fechado, o mês atual no ano corrente, 0 no futuro): a
 *     média e a comparação com o ano anterior usam esse período
 */
@Schema(name = "ReportResponse")
public record ReportView(
        ReportMetric metric,
        String metricLabel,
        String singular,
        String plural,
        String dateBasis,
        int year,
        String state,
        String sphere,
        String sphereLabel,
        List<Integer> availableYears,
        int elapsedMonths,
        Summary summary,
        List<MonthRow> months,
        List<YearRow> years,
        List<Slice> byAgency,
        List<Slice> byCity,
        List<Slice> byState,
        String kindTitle,
        List<Slice> byKind,
        Instant generatedAt) {

    /**
     * @param average média por mês no período que já passou
     * @param bestMonth mês de maior valor (1 a 12), nulo se o ano não tem movimento
     * @param worstMonth mês de menor valor entre os que tiveram movimento no período que já passou
     * @param previousSamePeriod o ano anterior nos mesmos meses (jan até {@code elapsedMonths})
     * @param changePercent variação sobre {@code previousSamePeriod}, nula se o ano anterior foi zero
     */
    @Schema(name = "ReportSummaryResponse")
    public record Summary(
            BigDecimal total,
            long count,
            BigDecimal average,
            Integer bestMonth,
            BigDecimal bestValue,
            Integer worstMonth,
            BigDecimal worstValue,
            BigDecimal previousSamePeriod,
            BigDecimal changePercent) {}

    /** Um mês do ano e o mesmo mês do ano anterior. {@code changePercent} é sobre o mês anterior. */
    @Schema(name = "ReportMonthResponse")
    public record MonthRow(
            int month,
            BigDecimal value,
            long count,
            BigDecimal previousYearValue,
            long previousYearCount,
            BigDecimal cumulative,
            BigDecimal previousYearCumulative,
            BigDecimal changePercent) {}

    /** O total de um ano, com os 12 meses (a tabela ano × mês). */
    @Schema(name = "ReportYearResponse")
    public record YearRow(int year, BigDecimal total, long count, List<BigDecimal> months) {}

    /**
     * Uma fatia de uma quebra (órgão, município, UF ou tipo). {@code key} é o id do órgão, a UF ou o código do tipo;
     * nulo em "Outros".
     */
    @Schema(name = "ReportSliceResponse")
    public record Slice(String key, String label, String detail, BigDecimal value, long count, BigDecimal share) {}
}
