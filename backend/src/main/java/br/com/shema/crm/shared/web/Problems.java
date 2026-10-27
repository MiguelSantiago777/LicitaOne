package br.com.shema.crm.shared.web;

import java.net.URI;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

/** Monta o ProblemDetail (RFC 7807) com as propriedades que a API sempre devolve. */
public final class Problems {

    private Problems() {}

    public static ProblemDetail of(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setTitle(code.title());
        problem.setType(URI.create(
                "urn:shemacrm:error:" + code.name().toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setProperty("code", code.name());
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        return problem;
    }
}
