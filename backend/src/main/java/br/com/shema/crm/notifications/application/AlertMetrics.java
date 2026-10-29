package br.com.shema.crm.notifications.application;

import br.com.shema.crm.contracts.ContractFollowUp;
import br.com.shema.crm.notifications.domain.AlertSettings;
import br.com.shema.crm.pricerecords.PriceRecordCalendar;
import br.com.shema.crm.shared.metrics.CachedGauge;
import br.com.shema.crm.tasks.AlertTasks;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Métricas de negócio dos alertas (seção 7), com os mesmos prazos das regras: contratos sem pedido, atas a vencer e
 * tarefas atrasadas. Os contadores de alertas gerados e de avisos mandados ficam no job e no consumidor.
 */
@Component
class AlertMetrics implements MeterBinder {

    private final ContractFollowUp contracts;
    private final PriceRecordCalendar records;
    private final AlertTasks tasks;
    private final AlertProperties properties;
    private final Clock clock;

    AlertMetrics(
            ContractFollowUp contracts,
            PriceRecordCalendar records,
            AlertTasks tasks,
            AlertProperties properties,
            Clock clock) {
        this.contracts = contracts;
        this.records = records;
        this.tasks = tasks;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        AlertSettings settings = properties.settings();
        gauge(
                registry,
                "shema.contracts.without.order",
                "Contratos assinados há " + settings.contractWithoutOrderDays() + " dias ou mais sem pedido",
                new CachedGauge(
                        "contratos sem pedido",
                        () -> contracts
                                .withoutOrderSignedOnOrBefore(
                                        LocalDate.now(clock).minusDays(settings.contractWithoutOrderDays()))
                                .size(),
                        clock));
        gauge(
                registry,
                "shema.price.records.expiring",
                "Atas que vencem nos próximos " + settings.widestExpiringBand() + " dias",
                new CachedGauge(
                        "atas a vencer",
                        () -> records.validUntilBetween(
                                        LocalDate.now(clock),
                                        LocalDate.now(clock).plusDays(settings.widestExpiringBand()))
                                .size(),
                        clock));
        gauge(
                registry,
                "shema.tasks.overdue",
                "Tarefas abertas com o vencimento já passado",
                new CachedGauge("tarefas atrasadas", tasks::countOverdue, clock));
    }

    private static void gauge(MeterRegistry registry, String name, String description, CachedGauge value) {
        Gauge.builder(name, value, CachedGauge::value)
                .strongReference(true)
                .description(description)
                .register(registry);
    }
}
