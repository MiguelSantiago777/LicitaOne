package br.com.shema.crm.shared.web;

import org.springframework.http.HttpStatus;

/** Códigos de erro estáveis da API. O front decide o que mostrar pelo código, nunca pelo texto. */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dados inválidos"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Requisição mal formada"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Não autenticado"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Sessão expirada"),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Conta bloqueada temporariamente"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Sem permissão"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Recurso não encontrado"),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "O registro foi alterado por outra pessoa"),
    CONFLICT(HttpStatus.CONFLICT, "Conflito com dados existentes"),
    BUSINESS_RULE(HttpStatus.UNPROCESSABLE_CONTENT, "Regra de negócio"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
