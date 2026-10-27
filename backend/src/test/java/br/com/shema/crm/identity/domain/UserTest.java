package br.com.shema.crm.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");
    private static final Duration LOCK = Duration.ofMinutes(15);

    private User newUser() {
        return new User("  Edwin  Lima ", " Edwin@Shema.COM.br ", "{bcrypt}hash", Role.SALES);
    }

    @Test
    void normalizesNameAndEmail() {
        User user = newUser();
        assertThat(user.getName()).isEqualTo("Edwin  Lima");
        assertThat(user.getEmail()).isEqualTo("edwin@shema.com.br");
        assertThat(user.isActive()).isTrue();
        assertThat(user.getId()).isNotNull();
    }

    @Test
    void initialsUseFirstAndLastName() {
        assertThat(newUser().initials()).isEqualTo("EL");
        assertThat(new User("maria", "m@x.com", "h", Role.VIEWER).initials()).isEqualTo("M");
        assertThat(new User("Ana Paula de Souza", "a@x.com", "h", Role.VIEWER).initials())
                .isEqualTo("AS");
    }

    @Test
    void rejectsMissingRequiredFields() {
        assertThatThrownBy(() -> new User(" ", "a@x.com", "h", Role.SALES))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new User("A", null, "h", Role.SALES)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new User("A", "a@x.com", "", Role.SALES)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new User("A", "a@x.com", "h", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void locksAfterMaxFailedAttempts() {
        User user = newUser();
        for (int i = 0; i < 4; i++) {
            assertThat(user.recordFailedLogin(NOW, 5, LOCK)).isFalse();
        }
        assertThat(user.getFailedLoginAttempts()).isEqualTo(4);
        assertThat(user.recordFailedLogin(NOW, 5, LOCK)).isTrue();
        assertThat(user.isLocked(NOW.plusSeconds(60))).isTrue();
        assertThat(user.canLogin(NOW.plusSeconds(60))).isFalse();
        assertThat(user.getLockedUntil()).isEqualTo(NOW.plus(LOCK));
    }

    @Test
    void lockExpiresAndCounterRestarts() {
        User user = newUser();
        for (int i = 0; i < 5; i++) {
            user.recordFailedLogin(NOW, 5, LOCK);
        }
        Instant afterLock = NOW.plus(LOCK);
        assertThat(user.isLocked(afterLock)).isFalse();
        assertThat(user.recordFailedLogin(afterLock, 5, LOCK)).isFalse();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void successfulLoginResetsCounters() {
        User user = newUser();
        user.recordFailedLogin(NOW, 5, LOCK);
        user.recordSuccessfulLogin(NOW);
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isEqualTo(NOW);
        assertThat(user.getRole().label()).isEqualTo("Comercial");
    }
}
