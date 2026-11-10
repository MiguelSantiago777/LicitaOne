package br.com.shema.crm.shared.ai;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * O que interessa de uma resposta do {@code generateContent}.
 *
 * @param content o {@code content} do primeiro candidato como veio (com as assinaturas de raciocínio), para voltar
 *     inteiro na rodada seguinte; nulo se a resposta foi bloqueada
 * @param finishReason STOP, MAX_TOKENS, SAFETY...; {@code BLOCKED} quando o próprio pedido foi bloqueado
 * @param outputTokens resposta mais raciocínio, que é como o Google cobra
 */
public record GeminiResponse(
        String text,
        List<FunctionCall> calls,
        JsonNode content,
        String finishReason,
        String model,
        long inputTokens,
        long outputTokens) {

    public record FunctionCall(String name, JsonNode args) {}

    public GeminiResponse {
        calls = List.copyOf(calls);
    }

    public static GeminiResponse from(JsonNode response, String requestedModel) {
        JsonNode usage = response.path("usageMetadata");
        long input = usage.path("promptTokenCount").asLong(0);
        long output = usage.path("candidatesTokenCount").asLong(0)
                + usage.path("thoughtsTokenCount").asLong(0);
        String model = response.path("modelVersion").asString(requestedModel);
        JsonNode candidate = response.path("candidates").path(0);
        if (candidate.isMissingNode()) {
            return new GeminiResponse("", List.of(), null, "BLOCKED", model, input, output);
        }
        StringBuilder text = new StringBuilder();
        List<FunctionCall> calls = new ArrayList<>();
        JsonNode content = candidate.path("content");
        for (JsonNode part : content.path("parts")) {
            if (part.path("thought").asBoolean(false)) {
                continue;
            }
            if (part.has("text")) {
                text.append(part.path("text").asString(""));
            }
            if (part.has("functionCall")) {
                JsonNode call = part.path("functionCall");
                calls.add(new FunctionCall(call.path("name").asString(""), call.path("args")));
            }
        }
        return new GeminiResponse(
                text.toString(),
                calls,
                content.isMissingNode() ? null : content,
                candidate.path("finishReason").asString("STOP"),
                model,
                input,
                output);
    }

    /** O modelo recusou por política (segurança, direitos autorais, dado pessoal) ou o pedido foi bloqueado. */
    public boolean refused() {
        return switch (finishReason) {
            case "BLOCKED", "SAFETY", "RECITATION", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "IMAGE_SAFETY" -> true;
            default -> false;
        };
    }

    public boolean truncated() {
        return "MAX_TOKENS".equals(finishReason);
    }
}
