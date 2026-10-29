package br.com.shema.crm.imports.application;

import br.com.shema.crm.imports.domain.ImportJob;
import br.com.shema.crm.imports.infrastructure.ImportJobRepository;
import br.com.shema.crm.shared.metrics.CachedGauge;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import org.springframework.stereotype.Component;

/** Métrica de negócio das importações (seção 7): quantas terminaram com falha. */
@Component
class ImportMetrics implements MeterBinder {

    private final ImportJobRepository jobs;
    private final Clock clock;

    ImportMetrics(ImportJobRepository jobs, Clock clock) {
        this.jobs = jobs;
        this.clock = clock;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        CachedGauge failed =
                new CachedGauge("importações com falha", () -> jobs.countByStatus(ImportJob.Status.FAILED), clock);
        Gauge.builder("shema.imports.failed", failed, CachedGauge::value)
                .strongReference(true)
                .description("Importações de planilha que terminaram com falha")
                .register(registry);
    }
}
