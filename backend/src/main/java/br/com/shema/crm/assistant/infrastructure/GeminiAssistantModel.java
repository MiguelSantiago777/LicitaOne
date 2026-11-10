package br.com.shema.crm.assistant.infrastructure;

import br.com.shema.crm.assistant.application.AssistantModel;
import br.com.shema.crm.assistant.application.AssistantProperties;
import br.com.shema.crm.shared.ai.GeminiClient;
import br.com.shema.crm.shared.ai.GeminiException;
import br.com.shema.crm.shared.ai.GeminiResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * O assistente com o Gemini (ADR 0021), com chamada de funções. Cada rodada é uma chamada, e quem executa as consultas
 * é o {@code AssistantService}, como no {@link ClaudeAssistantModel}. O {@code content} do modelo volta inteiro na
 * rodada seguinte, com as assinaturas de raciocínio, como a API pede.
 *
 * <p>A API do Gemini liga a resposta de uma função pelo nome, não por id; os ids das {@link ToolCall} são gerados
 * aqui, e o nome de cada um é achado de volta na conversa.
 */
class GeminiAssistantModel implements AssistantModel {

    private final GeminiClient client;
    private final AssistantProperties properties;
    private final JsonMapper json;

    GeminiAssistantModel(AssistantProperties properties, JsonMapper json) {
        this(new GeminiClient(properties.geminiApiKey(), Duration.ofMinutes(2), json), properties);
    }

    GeminiAssistantModel(GeminiClient client, AssistantProperties properties) {
        this.client = client;
        this.properties = properties;
        this.json = client.json();
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Reply next(String system, List<Entry> conversation, List<ToolSpec> tools) {
        JsonNode raw;
        try {
            raw = client.generate(properties.geminiModel(), params(system, conversation, tools));
        } catch (GeminiException e) {
            throw new ModelFailed(message(e), e);
        }
        GeminiResponse response = GeminiResponse.from(raw, properties.geminiModel());
        if (response.refused()) {
            throw new ModelFailed("A IA não aceitou responder esta pergunta.", null);
        }
        int round = (int)
                conversation.stream().filter(AssistantTurn.class::isInstance).count();
        List<ToolCall> calls = new ArrayList<>();
        for (GeminiResponse.FunctionCall call : response.calls()) {
            calls.add(new ToolCall("gemini-" + round + "-" + calls.size(), call.name(), input(call.args())));
        }
        return new Reply(
                new AssistantTurn(response.text(), calls, response.content()),
                response.model(),
                response.inputTokens(),
                response.outputTokens());
    }

    static String message(GeminiException e) {
        return switch (e.kind()) {
            case AUTH -> "A chave da IA foi recusada. Fale com o administrador.";
            case QUOTA ->
                e.dailyQuota()
                        ? "A cota gratuita da IA acabou por hoje. Tente de novo amanhã."
                        : "A IA está no limite de uso agora. Tente de novo em alguns minutos.";
            case UNAVAILABLE, BAD_REQUEST -> "A IA está ocupada ou fora do ar agora. Tente de novo em alguns minutos.";
            case OTHER -> "A IA respondeu com erro (" + e.status() + "). Tente de novo em alguns minutos.";
        };
    }

    ObjectNode params(String system, List<Entry> conversation, List<ToolSpec> tools) {
        ObjectNode body = json.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", system);
        if (!tools.isEmpty()) {
            ArrayNode declarations = body.putArray("tools").addObject().putArray("functionDeclarations");
            for (ToolSpec spec : tools) {
                ObjectNode schema = json.createObjectNode().put("type", "object");
                schema.set("properties", json.valueToTree(spec.properties()));
                schema.set("required", json.valueToTree(spec.required()));
                declarations
                        .addObject()
                        .put("name", spec.name())
                        .put("description", spec.description())
                        .set("parametersJsonSchema", schema);
            }
        }
        body.putObject("generationConfig").put("maxOutputTokens", properties.maxTokens());

        Map<String, String> callNames = new HashMap<>();
        ArrayNode contents = body.putArray("contents");
        for (Entry entry : conversation) {
            switch (entry) {
                case UserText u -> {
                    ObjectNode content = contents.addObject().put("role", "user");
                    content.putArray("parts").addObject().put("text", u.text());
                }
                case AssistantTurn a -> {
                    a.calls().forEach(c -> callNames.put(c.id(), c.name()));
                    if (a.nativeMessage() instanceof JsonNode node) {
                        contents.add(node);
                    } else {
                        contents.add(rebuilt(a));
                    }
                }
                case ToolResults r -> {
                    ObjectNode content = contents.addObject().put("role", "user");
                    ArrayNode parts = content.putArray("parts");
                    for (ToolResult result : r.results()) {
                        ObjectNode response =
                                json.createObjectNode().put(result.error() ? "error" : "result", result.content());
                        parts.addObject()
                                .putObject("functionResponse")
                                .put("name", callNames.getOrDefault(result.callId(), ""))
                                .set("response", response);
                    }
                }
            }
        }
        return body;
    }

    /** Turno sem o {@code content} original (conversa anterior, outro provedor): texto e chamadas remontados. */
    private ObjectNode rebuilt(AssistantTurn turn) {
        ObjectNode content = json.createObjectNode().put("role", "model");
        ArrayNode parts = content.putArray("parts");
        if (turn.text() != null && !turn.text().isBlank()) {
            parts.addObject().put("text", turn.text());
        }
        for (ToolCall call : turn.calls()) {
            parts.addObject()
                    .putObject("functionCall")
                    .put("name", call.name())
                    .set("args", json.valueToTree(call.input()));
        }
        if (parts.isEmpty()) {
            parts.addObject().put("text", "(sem texto)");
        }
        return content;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> input(JsonNode args) {
        if (args == null || !args.isObject()) {
            return Map.of();
        }
        try {
            Map<String, Object> map = json.treeToValue(args, Map.class);
            return map == null ? Map.of() : map;
        } catch (RuntimeException e) {
            return Map.of();
        }
    }
}
