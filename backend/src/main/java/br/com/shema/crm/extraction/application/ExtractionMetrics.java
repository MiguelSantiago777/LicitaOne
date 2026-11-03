package br.com.shema.crm.extraction.application;

import br.com.shema.crm.extraction.domain.Extraction;
import br.com.shema.crm.extraction.infrastructure.ExtractionRepository;
import br.com.shema.crm.shared.metrics.CachedGauge;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import org.springframework.stereotype.Component;

/** Métricas de negócio da leitura com IA: leituras por resultado, falhas acumuladas e tokens gastos (custo). */
@Component
class ExtractionMetrics implements MeterBinder {

    private final ExtractionRepository extractions;
    private final Clock clock;
    private MeterRegistry registry;

    ExtractionMetrics(ExtractionRepository extractions, Clock clock) {
        this.extractions = extractions;
        this.clock = clock;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        this.registry = registry;
        CachedGauge failed = new CachedGauge(
                "leituras com IA com falha", () -> extractions.countByStatus(Extraction.Status.FAILED), clock);
        Gauge.builder("shema.extraction.failed", failed, CachedGauge::value)
                .strongReference(true)
                .description("Leituras de documento com IA que terminaram com falha")
                .register(registry);
    }

    void finished(Extraction.Status status, Long inputTokens, Long outputTokens) {
        if (registry == null) {
            return;
        }
        Counter.builder("shema.extraction.finished")
                .description("Leituras de documento com IA concluídas, por resultado")
                .tag("status", status.name())
                .register(registry)
                .increment();
        tokens("input", inputTokens);
        tokens("output", outputTokens);
    }

    private void tokens(String type, Long count) {
        if (count == null || count <= 0) {
            return;
        }
        Counter.builder("shema.extraction.tokens")
                .description("Tokens gastos na leitura com IA (custo da API)")
                .tag("type", type)
                .register(registry)
                .increment(count);
    }
}
