package br.com.shema.crm.identity.api;

import br.com.shema.crm.identity.application.AuthResult;
import br.com.shema.crm.identity.application.AuthService;
import br.com.shema.crm.identity.infrastructure.AuthProperties;
import br.com.shema.crm.identity.infrastructure.LoginRateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticação")
@SecurityRequirements
class AuthController {

    static final String REFRESH_COOKIE = "shema_refresh";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final LoginRateLimiter rateLimiter;
    private final AuthProperties properties;

    AuthController(AuthService authService, LoginRateLimiter rateLimiter, AuthProperties properties) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @PostMapping("/login")
    @Operation(summary = "Entra com e-mail e senha", operationId = "login")
    ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        rateLimiter.check(http.getRemoteAddr());
        return respond(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Troca o refresh token do cookie por um novo access token", operationId = "refresh")
    ResponseEntity<AccessTokenResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String token) {
        return respond(authService.refresh(token));
    }

    @PostMapping("/logout")
    @Operation(summary = "Encerra a sessão", operationId = "logout")
    ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String token) {
        authService.logout(token);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseEntity<AccessTokenResponse> respond(AuthResult result) {
        AccessTokenResponse body = new AccessTokenResponse(
                result.accessToken(), "Bearer", result.accessTokenTtl().toSeconds(), UserResponse.from(result.user()));
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshCookie(result.refreshToken(), result.refreshTokenTtl())
                                .toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(properties.secureCookie())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
