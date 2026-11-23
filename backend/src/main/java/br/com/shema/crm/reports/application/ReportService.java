package br.com.shema.crm.reports.application;

import br.com.shema.crm.contracts.ContractFigures;
import br.com.shema.crm.pricerecords.CaronaFigures;
import br.com.shema.crm.reports.application.ReportBuilder.Fact;
import br.com.shema.crm.shared.reporting.MonthlyFigure;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import br.com.shema.crm.tenders.TenderFigures;
import br.com.shema.crm.tenders.TenderFigures.AgencyFacts;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Relatórios do balanço. Lê os totais por mês já somados pelos módulos donos e resolve o órgão de cada um (pedidos e
 * contratos chegam pela licitação). Todos os perfis veem.
 */
@Service
public class ReportService {

    private static final Set<String> SPHERES = Set.of("MUNICIPAL", "ESTADUAL", "FEDERAL", "OUTRA");
    private static final Map<String, String> SPHERE_LABELS =
            Map.of("MUNICIPAL", "Municipal", "ESTADUAL", "Estadual", "FEDERAL", "Federal", "OUTRA", "Outra");
    private static final int FIRST_YEAR = 2000;

    /** O PDF pronto, com o nome do arquivo. */
    public record Document(String fileName, byte[] content) {}

    private final TenderFigures tenders;
    private final ContractFigures contracts;
    private final CaronaFigures caronas;
    private final ReportDocuments documents;
    private final Clock clock;

    public ReportService(
            TenderFigures tenders,
            ContractFigures contracts,
            CaronaFigures caronas,
            ReportDocuments documents,
            Clock clock) {
        this.tenders = tenders;
        this.contracts = contracts;
        this.caronas = caronas;
        this.documents = documents;
        this.clock = clock;
    }

    public ReportView report(ReportMetric metric, Integer year, String state, String sphere) {
        YearMonth current = YearMonth.now(clock);
        int y = year == null ? current.getYear() : year;
        if (y < FIRST_YEAR || y > current.getYear() + 1) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Escolha um ano entre " + FIRST_YEAR + " e o próximo.");
        }
        String uf = blankToNull(state);
        if (uf != null && !uf.matches("[A-Za-z]{2}")) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "UF inválida.");
        }
        String sph = blankToNull(sphere);
        if (sph != null && !SPHERES.contains(sph.toUpperCase(Locale.ROOT))) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Esfera inválida.");
        }
        ReportBuilder.Filter filter = new ReportBuilder.Filter(
                uf == null ? null : uf.toUpperCase(Locale.ROOT), sph == null ? null : sph.toUpperCase(Locale.ROOT));
        return ReportBuilder.build(
                metric,
                y,
                filter,
                filter.sphere() == null ? null : SPHERE_LABELS.get(filter.sphere()),
                facts(metric),
                current,
                Instant.now(clock));
    }

    public Document pdf(ReportMetric metric, Integer year, String state, String sphere) {
        ReportView report = report(metric, year, state, sphere);
        String name = "Relatorio-" + plain(report.metricLabel()) + "-" + report.year()
                + (report.state() == null ? "" : "-" + report.state())
                + (report.sphere() == null ? "" : "-" + plain(report.sphereLabel()))
                + ".pdf";
        return new Document(name, documents.pdf(report));
    }

    private List<Fact> facts(ReportMetric metric) {
        List<MonthlyFigure> figures = switch (metric) {
            case PEDIDOS -> contracts.ordersByMonth();
            case CONTRATOS -> contracts.signedByMonth();
            case LICITACOES -> tenders.wonByMonth();
            case CARONAS -> caronas.caronasByMonth();
        };
        Set<UUID> tenderIds = new HashSet<>();
        figures.stream().map(MonthlyFigure::tenderId).filter(Objects::nonNull).forEach(tenderIds::add);
        Map<UUID, UUID> agencyOfTender = tenders.agencyOf(tenderIds);
        Set<UUID> agencyIds = new HashSet<>();
        for (MonthlyFigure f : figures) {
            UUID agency = agencyId(f, agencyOfTender);
            if (agency != null) {
                agencyIds.add(agency);
            }
        }
        Map<UUID, AgencyFacts> agencies = tenders.agencies(agencyIds);
        return figures.stream()
                .map(f -> {
                    UUID agency = agencyId(f, agencyOfTender);
                    return new Fact(
                            f.month(),
                            agency == null ? null : agencies.get(agency),
                            f.kind(),
                            f.kindLabel(),
                            f.count(),
                            f.value());
                })
                .toList();
    }

    private static UUID agencyId(MonthlyFigure f, Map<UUID, UUID> agencyOfTender) {
        return f.agencyId() != null ? f.agencyId() : f.tenderId() == null ? null : agencyOfTender.get(f.tenderId());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** "Licitações vencidas" → "licitacoes-vencidas", para o nome do arquivo. */
    private static String plain(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
