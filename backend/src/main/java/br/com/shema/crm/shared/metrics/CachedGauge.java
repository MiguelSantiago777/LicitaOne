package br.com.shema.crm.shared.metrics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Valor de uma métrica de negócio que vem de uma consulta: calculado no máximo uma vez por minuto, para a raspagem do
 * Prometheus não virar carga no banco. Se a consulta falhar, a métrica fica com o último valor conhecido.
 *
 * <p>Registre com {@code strongReference(true)}: o Micrometer guarda o objeto da gauge por referência fraca, e esta
 * instância não tem outro dono (sem isso, a métrica vira NaN depois da primeira coleta de lixo).
 */
public final class CachedGauge {

    private static final Logger log = LoggerFactory.getLogger(CachedGauge.class);

    private static final Duration TTL = Duration.ofMinutes(1);

    private final String name;
    private final LongSupplier query;
    private final Clock clock;
    private Instant computedAt;
    private double value = Double.NaN;

    public CachedGauge(String name, LongSupplier query, Clock clock) {
        this.name = name;
        this.query = query;
        this.clock = clock;
    }

    public synchronized double value() {
        Instant now = clock.instant();
        if (computedAt == null || computedAt.plus(TTL).isBefore(now)) {
            try {
                value = query.getAsLong();
            } catch (RuntimeException e) {
                log.warn("Métrica {} não pôde ser calculada: {}", name, e.getMessage());
            }
            computedAt = now;
        }
        return value;
    }
}
