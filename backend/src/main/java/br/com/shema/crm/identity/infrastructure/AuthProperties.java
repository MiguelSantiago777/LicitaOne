package br.com.shema.crm.identity.infrastructure;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração de autenticação.
 *
 * @param accessTokenTtl validade do access token (curto; vive só na memória do front)
 * @param refreshTokenTtl validade do refresh token (cookie HttpOnly)
 * @param maxFailedAttempts tentativas erradas seguidas antes de bloquear a conta
 * @param lockDuration tempo de bloqueio da conta
 * @param loginAttemptsPerMinute limite de tentativas de login por IP por minuto
 * @param secureCookie marca o cookie de refresh como Secure (desligar só em dev sem HTTPS)
 * @param issuer valor do claim iss
 * @param corsOrigins origens do front autorizadas a chamar a API
 */
@ConfigurationProperties("shema.auth")
public record AuthProperties(
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("7d") Duration refreshTokenTtl,
        @DefaultValue("5") int maxFailedAttempts,
        @DefaultValue("15m") Duration lockDuration,
        @DefaultValue("10") int loginAttemptsPerMinute,
        @DefaultValue("true") boolean secureCookie,
        @DefaultValue("shemacrm") String issuer,
        @DefaultValue("http://localhost:4300") List<String> corsOrigins) {}
