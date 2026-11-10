package br.com.shema.crm.shared.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Chamada ao {@code generateContent} da API do Gemini (Google AI Studio), por HTTP, sem SDK (ADR 0021). Tenta de novo
 * duas vezes quando a API está no limite por minuto (429) ou fora do ar (5xx, rede): no plano gratuito o limite por
 * minuto é baixo, e uma espera curta costuma bastar.
 */
public class GeminiClient {

    static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final Duration[] BACKOFF = {Duration.ofSeconds(3), Duration.ofSeconds(10)};

    private final HttpClient http;
    private final String apiKey;
    private final Duration timeout;
    private final JsonMapper json;
    private final String baseUrl;

    public GeminiClient(String apiKey, Duration timeout, JsonMapper json) {
        this(apiKey, timeout, json, BASE_URL);
    }

    /** Com outro endereço da API: para testes, com um servidor local. */
    public GeminiClient(String apiKey, Duration timeout, JsonMapper json, String baseUrl) {
        this.http =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.json = json;
        this.baseUrl = baseUrl;
    }

    public JsonMapper json() {
        return json;
    }

    /** Manda o pedido e devolve a resposta inteira; erro da API ou da rede vira {@link GeminiException}. */
    public JsonNode generate(String model, ObjectNode body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + model + ":generateContent"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        GeminiException last = null;
        for (int attempt = 0; attempt <= BACKOFF.length; attempt++) {
            if (attempt > 0) {
                pause(BACKOFF[attempt - 1]);
            }
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 == 2) {
                    return json.readTree(response.body());
                }
                last = GeminiException.from(response.statusCode(), response.body(), json);
            } catch (IOException e) {
                last = new GeminiException(GeminiException.Kind.UNAVAILABLE, 0, e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GeminiException(GeminiException.Kind.UNAVAILABLE, 0, "interrompido", e);
            }
            if (!last.retryable()) {
                throw last;
            }
        }
        throw last;
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeminiException(GeminiException.Kind.UNAVAILABLE, 0, "interrompido", e);
        }
    }
}
