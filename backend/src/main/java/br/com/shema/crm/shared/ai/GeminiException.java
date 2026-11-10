package br.com.shema.crm.shared.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Erro da API do Gemini, já classificado para a mensagem que a pessoa vê. */
public class GeminiException extends RuntimeException {

    public enum Kind {
        /** Chave inválida, revogada ou sem permissão. */
        AUTH,
        /** Limite por minuto ou cota do dia (no plano gratuito, a cota diária acaba). */
        QUOTA,
        /** Pedido recusado: arquivo que a API não abre, grande demais, esquema inválido. */
        BAD_REQUEST,
        /** API fora do ar, sobrecarregada ou rede. */
        UNAVAILABLE,
        OTHER
    }

    private final Kind kind;
    private final int status;

    public GeminiException(Kind kind, int status, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.status = status;
    }

    public Kind kind() {
        return kind;
    }

    public int status() {
        return status;
    }

    boolean retryable() {
        return kind == Kind.UNAVAILABLE || (kind == Kind.QUOTA && !dailyQuota());
    }

    /** A cota do dia acabou: tentar de novo agora não adianta. */
    public boolean dailyQuota() {
        String message = getMessage() == null ? "" : getMessage();
        return kind == Kind.QUOTA && message.contains("PerDay");
    }

    static GeminiException from(int status, String body, JsonMapper json) {
        String message = body;
        String reason = "";
        try {
            JsonNode error = json.readTree(body).path("error");
            if (!error.isMissingNode()) {
                message = error.path("message").asString("");
                for (JsonNode detail : error.path("details")) {
                    reason += detail.path("reason").asString("") + " ";
                    for (JsonNode violation : detail.path("violations")) {
                        message += " " + violation.path("quotaId").asString("");
                    }
                }
            }
        } catch (RuntimeException e) {
            // corpo que não é JSON: fica o texto como veio
        }
        Kind kind;
        if (status == 401 || status == 403 || reason.contains("API_KEY")) {
            kind = Kind.AUTH;
        } else if (status == 429) {
            kind = Kind.QUOTA;
        } else if (status == 400 || status == 404 || status == 413) {
            kind = Kind.BAD_REQUEST;
        } else if (status >= 500) {
            kind = Kind.UNAVAILABLE;
        } else {
            kind = Kind.OTHER;
        }
        return new GeminiException(kind, status, "Gemini " + status + ": " + message, null);
    }
}
