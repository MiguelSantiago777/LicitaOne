package br.com.shema.crm.reports.application;

import br.com.shema.crm.reports.application.ReportView.MonthRow;
import br.com.shema.crm.reports.application.ReportView.Slice;
import br.com.shema.crm.reports.application.ReportView.Summary;
import br.com.shema.crm.reports.application.ReportView.YearRow;
import br.com.shema.crm.tenders.TenderFigures.AgencyFacts;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Monta o relatório a partir dos totais já somados por mês. Sem banco e sem relógio: tudo entra por parâmetro, para os
 * testes conferirem as contas com números conhecidos.
 */
final class ReportBuilder {

    /** Quantas fatias as quebras por órgão e por município mostram; o resto vira "Outros". */
    static final int TOP = 10;

    /** Um total do mês já com o órgão resolvido. {@code agency} é nulo se o órgão não foi achado. */
    record Fact(YearMonth month, AgencyFacts agency, String kind, String kindLabel, long count, BigDecimal value) {}

    /** Filtro por órgão: UF e esfera, ambos opcionais. */
    record Filter(String state, String sphere) {

        boolean accepts(Fact f) {
            if (state == null && sphere == null) {
                return true;
            }
            AgencyFacts a = f.agency();
            return a != null
                    && (state == null || state.equalsIgnoreCase(a.state()))
                    && (sphere == null || sphere.equalsIgnoreCase(a.sphere()));
        }
    }

    private ReportBuilder() {}

    static ReportView build(
            ReportMetric metric,
            int year,
            Filter filter,
            String sphereLabel,
            List<Fact> allFacts,
            YearMonth current,
            Instant generatedAt) {
        List<Fact> facts = allFacts.stream().filter(filter::accepts).toList();
        int elapsed = year < current.getYear() ? 12 : year == current.getYear() ? current.getMonthValue() : 0;

        BigDecimal[] values = new BigDecimal[13];
        long[] counts = new long[13];
        BigDecimal[] previous = new BigDecimal[13];
        long[] previousCounts = new long[13];
        for (int m = 1; m <= 12; m++) {
            values[m] = BigDecimal.ZERO;
            previous[m] = BigDecimal.ZERO;
        }
        for (Fact f : facts) {
            int m = f.month().getMonthValue();
            if (f.month().getYear() == year) {
                values[m] = values[m].add(f.value());
                counts[m] += f.count();
            } else if (f.month().getYear() == year - 1) {
                previous[m] = previous[m].add(f.value());
                previousCounts[m] += f.count();
            }
        }

        List<MonthRow> months = new ArrayList<>();
        BigDecimal cumulative = BigDecimal.ZERO;
        BigDecimal previousCumulative = BigDecimal.ZERO;
        for (int m = 1; m <= 12; m++) {
            cumulative = cumulative.add(values[m]);
            previousCumulative = previousCumulative.add(previous[m]);
            BigDecimal change = m == 1 ? null : percent(values[m], values[m - 1]);
            months.add(new MonthRow(
                    m,
                    values[m],
                    counts[m],
                    previous[m],
                    previousCounts[m],
                    // Mês que ainda não chegou não tem acumulado: a linha do gráfico para no mês atual
                    m <= Math.max(elapsed, lastMonthWithValue(values)) ? cumulative : null,
                    previousCumulative,
                    m <= elapsed ? change : null));
        }

        return new ReportView(
                metric,
                metric.label(),
                metric.singular(),
                metric.plural(),
                metric.dateBasis(),
                year,
                filter.state(),
                filter.sphere(),
                sphereLabel,
                availableYears(allFacts, current),
                elapsed,
                summary(values, counts, previous, elapsed),
                months,
                years(facts),
                byAgency(facts, year),
                byCity(facts, year),
                byState(facts, year),
                metric.kindTitle(),
                metric.kindTitle() == null ? List.of() : byKind(facts, year),
                generatedAt);
    }

    private static int lastMonthWithValue(BigDecimal[] values) {
        for (int m = 12; m >= 1; m--) {
            if (values[m].signum() != 0) {
                return m;
            }
        }
        return 0;
    }

    private static Summary summary(BigDecimal[] values, long[] counts, BigDecimal[] previous, int elapsed) {
        BigDecimal total = BigDecimal.ZERO;
        long count = 0;
        Integer best = null;
        Integer worst = null;
        for (int m = 1; m <= 12; m++) {
            total = total.add(values[m]);
            count += counts[m];
            if (values[m].signum() > 0) {
                if (best == null || values[m].compareTo(values[best]) > 0) {
                    best = m;
                }
                if (m <= elapsed && (worst == null || values[m].compareTo(values[worst]) < 0)) {
                    worst = m;
                }
            }
        }
        BigDecimal samePeriod = BigDecimal.ZERO;
        BigDecimal currentPeriod = BigDecimal.ZERO;
        for (int m = 1; m <= elapsed; m++) {
            samePeriod = samePeriod.add(previous[m]);
            currentPeriod = currentPeriod.add(values[m]);
        }
        BigDecimal average = elapsed == 0
                ? BigDecimal.ZERO.setScale(2)
                : currentPeriod.divide(BigDecimal.valueOf(elapsed), 2, RoundingMode.HALF_UP);
        return new Summary(
                total,
                count,
                average,
                best,
                best == null ? null : values[best],
                // Com um mês só de movimento, o pior seria o mesmo que o melhor: não mostra
                Objects.equals(worst, best) ? null : worst,
                Objects.equals(worst, best) || worst == null ? null : values[worst],
                samePeriod,
                elapsed == 0 ? null : percent(currentPeriod, samePeriod));
    }

    /** Variação em %, com uma casa; nula se a base é zero. */
    static BigDecimal percent(BigDecimal value, BigDecimal base) {
        if (base == null || base.signum() == 0) {
            return null;
        }
        return value.subtract(base).multiply(BigDecimal.valueOf(100)).divide(base, 1, RoundingMode.HALF_UP);
    }

    /** Do primeiro ano com movimento (em qualquer filtro) até o atual. */
    private static List<Integer> availableYears(List<Fact> facts, YearMonth current) {
        int first = facts.stream().mapToInt(f -> f.month().getYear()).min().orElse(current.getYear());
        int last = Math.max(
                current.getYear(),
                facts.stream().mapToInt(f -> f.month().getYear()).max().orElse(current.getYear()));
        List<Integer> years = new ArrayList<>();
        for (int y = last; y >= first; y--) {
            years.add(y);
        }
        return years;
    }

    private static List<YearRow> years(List<Fact> facts) {
        TreeSet<Integer> set = new TreeSet<>();
        facts.forEach(f -> set.add(f.month().getYear()));
        List<YearRow> rows = new ArrayList<>();
        for (int y : set) {
            BigDecimal[] m = new BigDecimal[12];
            java.util.Arrays.fill(m, BigDecimal.ZERO);
            BigDecimal total = BigDecimal.ZERO;
            long count = 0;
            for (Fact f : facts) {
                if (f.month().getYear() == y) {
                    int i = f.month().getMonthValue() - 1;
                    m[i] = m[i].add(f.value());
                    total = total.add(f.value());
                    count += f.count();
                }
            }
            rows.add(new YearRow(y, total, count, List.of(m)));
        }
        return rows;
    }

    private static List<Slice> byAgency(List<Fact> facts, int year) {
        return slices(
                facts,
                year,
                f -> f.agency() == null ? null : f.agency().id().toString(),
                f -> f.agency() == null ? "Órgão não encontrado" : f.agency().name(),
                f -> f.agency() == null
                        ? null
                        : f.agency().city() + "/" + f.agency().state(),
                TOP);
    }

    private static List<Slice> byCity(List<Fact> facts, int year) {
        return slices(
                facts,
                year,
                f -> f.agency() == null
                        ? null
                        : f.agency().city() + "/" + f.agency().state(),
                f -> f.agency() == null ? "Sem município" : f.agency().city(),
                f -> f.agency() == null ? null : f.agency().state(),
                TOP);
    }

    private static List<Slice> byState(List<Fact> facts, int year) {
        return slices(
                facts,
                year,
                f -> f.agency() == null ? null : f.agency().state(),
                f -> f.agency() == null ? "Sem UF" : f.agency().state(),
                f -> null,
                Integer.MAX_VALUE);
    }

    private static List<Slice> byKind(List<Fact> facts, int year) {
        return slices(
                facts,
                year,
                Fact::kind,
                f -> f.kindLabel() == null ? "Sem tipo" : f.kindLabel(),
                f -> null,
                Integer.MAX_VALUE);
    }

    /**
     * Soma as fatias do ano pela chave, da maior para a menor (no empate, pelo nome). Passando de {@code top}, as
     * demais viram uma fatia "Outros" no fim.
     */
    private static List<Slice> slices(
            List<Fact> facts,
            int year,
            Function<Fact, String> key,
            Function<Fact, String> label,
            Function<Fact, String> detail,
            int top) {
        Map<String, Slice> sums = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Fact f : facts) {
            if (f.month().getYear() != year) {
                continue;
            }
            total = total.add(f.value());
            String k = Objects.requireNonNullElse(key.apply(f), "");
            Slice old = sums.get(k);
            sums.put(
                    k,
                    old == null
                            ? new Slice(key.apply(f), label.apply(f), detail.apply(f), f.value(), f.count(), null)
                            : new Slice(
                                    old.key(),
                                    old.label(),
                                    old.detail(),
                                    old.value().add(f.value()),
                                    old.count() + f.count(),
                                    null));
        }
        List<Slice> sorted = new ArrayList<>(sums.values());
        sorted.sort(Comparator.comparing(Slice::value)
                .reversed()
                .thenComparing(Slice::label, String.CASE_INSENSITIVE_ORDER));
        List<Slice> result = new ArrayList<>();
        BigDecimal others = BigDecimal.ZERO;
        long othersCount = 0;
        int othersSlices = 0;
        for (int i = 0; i < sorted.size(); i++) {
            Slice s = sorted.get(i);
            if (i < top) {
                result.add(withShare(s, total));
            } else {
                others = others.add(s.value());
                othersCount += s.count();
                othersSlices++;
            }
        }
        if (othersSlices > 0) {
            result.add(
                    withShare(new Slice(null, "Outros", othersSlices + " a mais", others, othersCount, null), total));
        }
        return result;
    }

    private static Slice withShare(Slice s, BigDecimal total) {
        BigDecimal share = total.signum() == 0
                ? BigDecimal.ZERO.setScale(1)
                : s.value().multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
        return new Slice(s.key(), s.label(), s.detail(), s.value(), s.count(), share);
    }
}
