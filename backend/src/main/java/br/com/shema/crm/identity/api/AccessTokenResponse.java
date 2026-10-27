package br.com.shema.crm.identity.api;

/** O refresh token não vem aqui: vai num cookie HttpOnly. */
public record AccessTokenResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {}
