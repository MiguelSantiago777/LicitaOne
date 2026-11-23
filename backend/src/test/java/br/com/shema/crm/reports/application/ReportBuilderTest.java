package br.com.shema.crm.reports.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.shema.crm.reports.application.ReportBuilder.Fact;
import br.com.shema.crm.reports.application.ReportBuilder.Filter;
import br.com.shema.crm.reports.application.ReportView.MonthRow;
import br.com.shema.crm.reports.application.ReportView.Slice;
import br.com.shema.crm.tenders.TenderFigures.AgencyFacts;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportBuilderTest {

    private static final AgencyFacts MAGE = agency("Prefeitura de Magé", "Magé", "RJ", "MUNICIPAL");
    private static final AgencyFacts NITEROI = agency("Prefeitura de Niterói", "Niterói", "RJ", "MUNICIPAL");
    private static final AgencyFacts SEFAZ = agency("SEFAZ RJ", "Rio de Janeiro", "RJ", "ESTADUAL");
    private static final AgencyFacts BH = agency("Prefeitura de Belo Horizonte", "Belo Horizonte", "MG", "MUNICIPAL");

    private static final Filter ALL = new Filter(null, null);
    /** "Hoje" é novembro de 2026: 11 meses do ano já passaram. */
    private static final YearMonth NOW = YearMonth.of(2026, 11);

    private static AgencyFacts agency(String name, String city, String state, String sphere) {
        return new AgencyFacts(UUID.randomUUID(), name, city, state, sphere, sphere);
    }

    private static Fact fact(int year, int month, AgencyFacts agency, String kind, long count, String value) {
        return new Fact(YearMonth.of(year, month), agency, kind, kind, count, new BigDecimal(value));
    }

    private static ReportView build(int year, Filter filter, List<Fact> facts) {
        return ReportBuilder.build(ReportMetric.PEDIDOS, year, filter, null, facts, NOW, Instant.EPOCH);
    }

    private static List<Fact> sample() {
        return List.of(
                fact(2026, 1, MAGE, "EMPENHO", 2, "1000.00"),
                fact(2026, 3, NITEROI, "CONTRATO", 1, "5000.00"),
                fact(2026, 3, MAGE, "EMPENHO", 1, "500.00"),
                fact(2026, 7, SEFAZ, "CONTRATO", 3, "2500.00"),
                fact(2026, 12, BH, "DISPENSA", 1, "9999.00"), // depois de "hoje": conta no total, não na média
                fact(2025, 1, MAGE, "EMPENHO", 1, "800.00"),
                fact(2025, 3, MAGE, "EMPENHO", 1, "4000.00"),
                fact(2025, 12, MAGE, "EMPENHO", 1, "7000.00"),
                fact(2024, 6, NITEROI, "CONTRATO", 1, "300.00"));
    }

    @Test
    void monthsTotalsAndBestMonth() {
        ReportView r = build(2026, ALL, sample());
        assertThat(r.elapsedMonths()).isEqualTo(11);
        assertThat(r.months()).hasSize(12);
        MonthRow march = r.months().get(2);
        assertThat(march.value()).isEqualByComparingTo("5500.00");
        assertThat(march.count()).isEqualTo(2);
        assertThat(march.previousYearValue()).isEqualByComparingTo("4000.00");
        assertThat(march.cumulative()).isEqualByComparingTo("6500.00");

        var s = r.summary();
        assertThat(s.total()).isEqualByComparingTo("18999.00");
        assertThat(s.count()).isEqualTo(8);
        assertThat(s.bestMonth()).isEqualTo(12);
        // O mais fraco é entre os meses que já passaram e tiveram movimento
        assertThat(s.worstMonth()).isEqualTo(1);
        // Média: só jan–nov (9.000) dividido por 11 meses
        assertThat(s.average()).isEqualByComparingTo("818.18");
        // Jan–nov de 2025 = 4.800; de 2026 = 9.000: +87,5%
        assertThat(s.previousSamePeriod()).isEqualByComparingTo("4800.00");
        assertThat(s.changePercent()).isEqualByComparingTo("87.5");
    }

    @Test
    void closedYearUsesTwelveMonthsAndChangeOverPreviousMonth() {
        ReportView r = build(2025, ALL, sample());
        assertThat(r.elapsedMonths()).isEqualTo(12);
        assertThat(r.summary().average()).isEqualByComparingTo("983.33");
        assertThat(r.summary().bestMonth()).isEqualTo(12);
        assertThat(r.months().get(0).changePercent()).isNull();
        // Fevereiro zerado depois de janeiro com 800: -100%; março sobre fevereiro zerado: sem base
        assertThat(r.months().get(1).changePercent()).isEqualByComparingTo("-100.0");
        assertThat(r.months().get(2).changePercent()).isNull();
        // 2024 teve só 300 em junho: base para a comparação do ano todo
        assertThat(r.summary().previousSamePeriod()).isEqualByComparingTo("300.00");
    }

    @Test
    void yearsAndAvailableYears() {
        ReportView r = build(2026, ALL, sample());
        assertThat(r.availableYears()).containsExactly(2026, 2025, 2024);
        assertThat(r.years()).extracting(ReportView.YearRow::year).containsExactly(2024, 2025, 2026);
        assertThat(r.years().get(1).total()).isEqualByComparingTo("11800.00");
        assertThat(r.years().get(1).months().get(11)).isEqualByComparingTo("7000.00");
    }

    @Test
    void breakdownsByAgencyCityStateAndKind() {
        ReportView r = build(2026, ALL, sample());
        assertThat(r.byAgency())
                .extracting(Slice::label)
                .containsExactly(
                        "Prefeitura de Belo Horizonte", "Prefeitura de Niterói", "SEFAZ RJ", "Prefeitura de Magé");
        Slice mage = r.byAgency().get(3);
        assertThat(mage.value()).isEqualByComparingTo("1500.00");
        assertThat(mage.count()).isEqualTo(3);
        assertThat(mage.share()).isEqualByComparingTo("7.9");
        assertThat(r.byState()).extracting(Slice::key).containsExactly("MG", "RJ");
        assertThat(r.byState().get(1).value()).isEqualByComparingTo("9000.00");
        assertThat(r.byKind()).extracting(Slice::key).containsExactly("DISPENSA", "CONTRATO", "EMPENHO");
        assertThat(r.kindTitle()).isEqualTo("Por tipo de contrato");
    }

    @Test
    void moreThanTenAgenciesFoldIntoOthers() {
        List<Fact> facts = new ArrayList<>();
        for (int i = 1; i <= 13; i++) {
            facts.add(fact(2026, 2, agency("Órgão " + i, "Cidade " + i, "RJ", "MUNICIPAL"), "EMPENHO", 1, i + "00.00"));
        }
        ReportView r = build(2026, ALL, facts);
        assertThat(r.byAgency()).hasSize(11);
        Slice others = r.byAgency().get(10);
        assertThat(others.key()).isNull();
        assertThat(others.label()).isEqualTo("Outros");
        assertThat(others.detail()).isEqualTo("3 a mais");
        assertThat(others.value()).isEqualByComparingTo("600.00"); // 100 + 200 + 300
        assertThat(r.byAgency().getFirst().label()).isEqualTo("Órgão 13");
    }

    @Test
    void filterByStateAndSphere() {
        ReportView rj = build(2026, new Filter("RJ", null), sample());
        assertThat(rj.summary().total()).isEqualByComparingTo("9000.00");
        ReportView municipalRj = build(2026, new Filter("RJ", "MUNICIPAL"), sample());
        assertThat(municipalRj.summary().total()).isEqualByComparingTo("6500.00");
        // O filtro não esconde anos: a lista de anos vem de todos os registros
        assertThat(municipalRj.availableYears()).containsExactly(2026, 2025, 2024);
    }

    @Test
    void emptyYearHasNoBestMonthAndNoCrash() {
        ReportView r = build(2023, ALL, sample());
        assertThat(r.summary().total()).isEqualByComparingTo("0");
        assertThat(r.summary().bestMonth()).isNull();
        assertThat(r.summary().worstMonth()).isNull();
        assertThat(r.byAgency()).isEmpty();
        // 2022 também zerado: sem base para a variação
        assertThat(r.summary().changePercent()).isNull();
    }

    @Test
    void caronasHaveNoKindBreakdown() {
        ReportView r = ReportBuilder.build(ReportMetric.CARONAS, 2026, ALL, null, sample(), NOW, Instant.EPOCH);
        assertThat(r.kindTitle()).isNull();
        assertThat(r.byKind()).isEmpty();
    }
}
