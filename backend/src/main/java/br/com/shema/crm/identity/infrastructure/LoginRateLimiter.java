package br.com.shema.crm.identity.infrastructure;

import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Limita tentativas de login por IP (em memória; suficiente para uma instância, ver ADR 0004). */
@Component
public class LoginRateLimiter {

    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int attemptsPerMinute;

    public LoginRateLimiter(AuthProperties properties) {
        this.attemptsPerMinute = properties.loginAttemptsPerMinute();
    }

    public void check(String clientKey) {
        if (buckets.size() > MAX_TRACKED_CLIENTS) {
            buckets.clear();
        }
        Bucket bucket = buckets.computeIfAbsent(
                clientKey,
                key -> Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(attemptsPerMinute)
                                .refillGreedy(attemptsPerMinute, Duration.ofMinutes(1))
                                .build())
                        .build());
        if (!bucket.tryConsume(1)) {
            throw new ApiException(ErrorCode.RATE_LIMITED, "Muitas tentativas de login. Aguarde um minuto.");
        }
    }
}
