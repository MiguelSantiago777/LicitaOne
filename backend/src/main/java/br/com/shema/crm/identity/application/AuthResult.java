package br.com.shema.crm.identity.application;

import br.com.shema.crm.identity.domain.User;
import java.time.Duration;

/** Resultado de login ou refresh: access token (vai no corpo) e refresh token (vai no cookie). */
public record AuthResult(
        String accessToken, Duration accessTokenTtl, String refreshToken, Duration refreshTokenTtl, User user) {}
