package br.com.shema.crm.identity.application;

import br.com.shema.crm.identity.domain.RefreshToken;
import br.com.shema.crm.identity.domain.User;
import br.com.shema.crm.identity.infrastructure.AuthProperties;
import br.com.shema.crm.identity.infrastructure.RefreshTokenRepository;
import br.com.shema.crm.identity.infrastructure.UserRepository;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final AuthProperties properties;
    private final Clock clock;
    private final String keyId;
    /** Hash usado quando o e-mail não existe, para o tempo de resposta não revelar quais e-mails têm conta. */
    private final String dummyHash;

    public AuthService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            AuthProperties properties,
            Clock clock,
            com.nimbusds.jose.jwk.RSAKey jwtSigningKey) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
        this.keyId = jwtSigningKey.getKeyID();
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult login(String email, String password) {
        Instant now = clock.instant();
        User user = users.findByEmail(User.normalizeEmail(email)).orElse(null);
        if (user == null || !user.isActive()) {
            passwordEncoder.matches(password, dummyHash);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.isLocked(now)) {
            throw lockedError();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            boolean locked = user.recordFailedLogin(now, properties.maxFailedAttempts(), properties.lockDuration());
            if (locked) {
                log.warn("Conta {} bloqueada após tentativas de login sem sucesso", user.getId());
                throw lockedError();
            }
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        user.recordSuccessfulLogin(now);
        return issueTokens(user, now);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken current = rawRefreshToken == null
                ? null
                : refreshTokens
                        .findByTokenHash(TokenHasher.sha256(rawRefreshToken))
                        .orElse(null);
        if (current == null) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (current.isRevoked()) {
            // Token já usado sendo reapresentado: possível roubo. Derruba todas as sessões do usuário.
            log.warn("Reuso de refresh token revogado para o usuário {}", current.getUserId());
            refreshTokens.revokeAllForUser(current.getUserId(), now);
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (!current.isActive(now)) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        User user = users.findById(current.getUserId())
                .filter(u -> u.canLogin(now))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));
        current.revoke(now);
        return issueTokens(user, now);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null) {
            return;
        }
        refreshTokens
                .findByTokenHash(TokenHasher.sha256(rawRefreshToken))
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    private AuthResult issueTokens(User user, Instant now) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .claim("name", user.getName())
                .claim("email", user.getEmail())
                .claim("roles", List.of(user.getRole().name()))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
        String accessToken =
                jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        String rawRefresh = TokenHasher.newOpaqueToken();
        refreshTokens.save(
                new RefreshToken(user.getId(), TokenHasher.sha256(rawRefresh), now, properties.refreshTokenTtl()));
        return new AuthResult(accessToken, properties.accessTokenTtl(), rawRefresh, properties.refreshTokenTtl(), user);
    }

    private ApiException lockedError() {
        return new ApiException(
                ErrorCode.ACCOUNT_LOCKED,
                "Conta bloqueada por excesso de tentativas. Tente de novo em "
                        + properties.lockDuration().toMinutes() + " minutos.");
    }
}
