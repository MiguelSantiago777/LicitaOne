package br.com.shema.crm.notifications.application;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockingTaskExecutor.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Roda o job de alertas sob o lock do ShedLock: pelo agendamento diário ou pelo "verificar agora" do administrador. Se
 * outra instância (ou outro clique) já está rodando, esta não roda.
 */
@Component
public class AlertRunner {

    public static final String LOCK = "alert-scan";

    /** Se travar, o lock se solta sozinho depois disso. */
    private static final Duration LOCK_AT_MOST = Duration.ofMinutes(15);

    /**
     * O agendado segura o lock por alguns minutos mesmo terminando antes: outra instância, com o relógio um pouco
     * atrasado, não roda o mesmo agendamento de novo.
     */
    private static final Duration SCHEDULED_LOCK_AT_LEAST = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(AlertRunner.class);

    private final LockingTaskExecutor executor;
    private final AlertScan scan;
    private final Clock clock;

    AlertRunner(LockingTaskExecutor executor, AlertScan scan, Clock clock) {
        this.executor = executor;
        this.scan = scan;
        this.clock = clock;
    }

    @Scheduled(cron = "${shema.alerts.cron}", zone = "America/Sao_Paulo")
    void daily() {
        if (run(SCHEDULED_LOCK_AT_LEAST).isEmpty()) {
            log.info("Alertas: outra instância já está rodando o job de hoje");
        }
    }

    /** "Verificar agora": vazio se o job já está rodando. */
    public Optional<AlertScan.Result> runNow() {
        return run(Duration.ZERO);
    }

    private Optional<AlertScan.Result> run(Duration lockAtLeast) {
        LockConfiguration lock = new LockConfiguration(clock.instant(), LOCK, LOCK_AT_MOST, lockAtLeast);
        TaskResult<AlertScan.Result> result;
        try {
            result = executor.executeWithLock(scan::run, lock);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
        return result.wasExecuted() ? Optional.of(result.getResult()) : Optional.empty();
    }
}
