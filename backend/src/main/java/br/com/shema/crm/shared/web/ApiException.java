package br.com.shema.crm.shared.web;

/** Erro de negócio ou de acesso que vira um ProblemDetail com código estável. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public ApiException(ErrorCode code) {
        this(code, code.title());
    }

    public ErrorCode code() {
        return code;
    }
}
