package br.com.shema.crm.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Refresh token guardado só como hash SHA-256. Cada uso gera um token novo (rotação). */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Revogado por ter sido trocado por um novo (rotação), e não por logout ou por suspeita de roubo. */
    @Column(nullable = false)
    private boolean rotated;

    protected RefreshToken() {}

    public RefreshToken(UUID userId, String tokenHash, Instant now, Duration ttl) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.createdAt = now;
        this.expiresAt = now.plus(ttl);
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isRotated() {
        return rotated;
    }

    /** Troca por um token novo: revoga este e lembra que foi rotação. */
    public void rotate(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
            rotated = true;
        }
    }

    /**
     * Foi trocado há pouco? Um navegador que recarrega a página no meio de uma renovação descarta a resposta e
     * reapresenta o token anterior. Dentro desta janela isso é tratado como corrida, não como roubo.
     */
    public boolean wasRotatedWithin(Instant now, java.time.Duration grace) {
        return rotated && revokedAt != null && !now.isAfter(revokedAt.plus(grace));
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
