package br.com.shema.crm.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");

    @Test
    void activeUntilExpiration() {
        RefreshToken token = new RefreshToken(UUID.randomUUID(), "a".repeat(64), NOW, Duration.ofDays(7));
        assertThat(token.isActive(NOW.plus(Duration.ofDays(6)))).isTrue();
        assertThat(token.isActive(NOW.plus(Duration.ofDays(7)))).isFalse();
        assertThat(token.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void revokeIsPermanentAndKeepsFirstTimestamp() {
        RefreshToken token = new RefreshToken(UUID.randomUUID(), "a".repeat(64), NOW, Duration.ofDays(7));
        token.revoke(NOW.plusSeconds(10));
        token.revoke(NOW.plusSeconds(20));
        assertThat(token.isRevoked()).isTrue();
        assertThat(token.isActive(NOW.plusSeconds(30))).isFalse();
        assertThat(token.getRevokedAt()).isEqualTo(NOW.plusSeconds(10));
    }
}
