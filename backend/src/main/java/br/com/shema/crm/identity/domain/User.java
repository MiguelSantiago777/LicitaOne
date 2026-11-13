package br.com.shema.crm.identity.domain;

import br.com.shema.crm.shared.domain.BrDocuments;
import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.OptimisticLock;

/**
 * Usuário. Os campos de controle de login ficam fora do optimistic locking: dois logins simultâneos do mesmo
 * usuário não podem dar conflito de versão.
 */
@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean active;

    /** Cargo e CPF de quem assina as propostas de preços (Fase 12). Opcionais. */
    @Column(name = "job_title", length = 120)
    private String jobTitle;

    @Column(length = 11)
    private String cpf;

    @OptimisticLock(excluded = true)
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @OptimisticLock(excluded = true)
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @OptimisticLock(excluded = true)
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected User() {}

    public User(String name, String email, String passwordHash, Role role) {
        this.id = UUID.randomUUID();
        this.name = requireText(name, "name").trim();
        this.email = normalizeEmail(email);
        this.passwordHash = requireText(passwordHash, "passwordHash");
        this.role = Objects.requireNonNull(role, "role");
        this.active = true;
    }

    public static String normalizeEmail(String email) {
        return requireText(email, "email").trim().toLowerCase(Locale.ROOT);
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public boolean canLogin(Instant now) {
        return active && !isLocked(now);
    }

    /** Conta a falha e bloqueia a conta quando atinge o limite. Devolve true se a conta ficou bloqueada. */
    public boolean recordFailedLogin(Instant now, int maxAttempts, Duration lockDuration) {
        if (lockedUntil != null && !now.isBefore(lockedUntil)) {
            failedLoginAttempts = 0;
            lockedUntil = null;
        }
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            failedLoginAttempts = 0;
            return true;
        }
        return false;
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public void changeProfile(String name, Role role, boolean active) {
        this.name = requireText(name, "name").trim();
        this.role = Objects.requireNonNull(role, "role");
        this.active = active;
    }

    /** Cargo e CPF que vão na assinatura da proposta. Em branco apaga. */
    public void changeSignerData(String jobTitle, String cpf) {
        String title = jobTitle == null || jobTitle.isBlank() ? null : jobTitle.trim();
        if (title != null && title.length() > 120) {
            throw new DomainRuleViolation("TOO_LONG", "Use no máximo 120 caracteres no cargo.");
        }
        this.jobTitle = title;
        this.cpf = BrDocuments.cpf(cpf);
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = requireText(passwordHash, "passwordHash");
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    /** Iniciais para o avatar: primeira letra do primeiro e do último nome. */
    public String initials() {
        String[] parts =
                Arrays.stream(name.split("\\s+")).filter(p -> !p.isBlank()).toArray(String[]::new);
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(Locale.ROOT);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        return value;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public String getCpf() {
        return cpf;
    }

    public boolean isActive() {
        return active;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
