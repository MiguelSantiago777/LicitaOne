package br.com.shema.crm.shared.ai;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** API do Gemini falsa, em localhost: devolve as respostas na ordem e guarda os pedidos. Sem rede, sem chave. */
public final class FakeGemini implements AutoCloseable {

    public record Response(int status, String body) {}

    private final HttpServer server;
    private final Deque<Response> responses = new ArrayDeque<>();
    private final List<JsonNode> requests = new ArrayList<>();
    private final List<String> paths = new ArrayList<>();
    private final JsonMapper json = JsonMapper.builder().build();

    public FakeGemini() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            synchronized (this) {
                requests.add(json.readTree(body));
                paths.add(exchange.getRequestURI().getPath());
            }
            Response response = responses.isEmpty() ? new Response(500, "{}") : responses.poll();
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    public FakeGemini reply(int status, String body) {
        responses.add(new Response(status, body));
        return this;
    }

    /** Resposta de sucesso com um texto (o JSON da saída estruturada) e o uso de tokens. */
    public FakeGemini replyText(String text) {
        return reply(200, """
                {"candidates":[{"finishReason":"STOP","content":{"role":"model","parts":[{"text":%s}]}}],
                 "modelVersion":"gemini-3.8-flash",
                 "usageMetadata":{"promptTokenCount":1200,"candidatesTokenCount":300,"thoughtsTokenCount":100}}
                """.formatted(json.writeValueAsString(text)));
    }

    public GeminiClient client() {
        return new GeminiClient(
                "chave-de-teste",
                Duration.ofSeconds(10),
                json,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta/models/");
    }

    public synchronized List<JsonNode> requests() {
        return List.copyOf(requests);
    }

    public synchronized List<String> paths() {
        return List.copyOf(paths);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
