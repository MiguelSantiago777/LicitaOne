package br.com.shema.crm.assistant.infrastructure;

import br.com.shema.crm.assistant.application.AssistantModel;
import br.com.shema.crm.assistant.application.AssistantProperties;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlockParam;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * O assistente com o Claude (SDK oficial), num laço de ferramentas feito aqui: cada rodada é uma chamada, e quem
 * executa as consultas é o {@code AssistantService}. A mensagem do modelo volta inteira na rodada seguinte
 * ({@link Message#toParam()}), com o raciocínio, como a API pede.
 */
class ClaudeAssistantModel implements AssistantModel {

    private final AnthropicClient client;
    private final AssistantProperties properties;

    ClaudeAssistantModel(AssistantProperties properties) {
        this(
                AnthropicOkHttpClient.builder()
                        .apiKey(properties.apiKey())
                        .timeout(Duration.ofMinutes(2))
                        .maxRetries(2)
                        .build(),
                properties);
    }

    ClaudeAssistantModel(AnthropicClient client, AssistantProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Reply next(String system, List<Entry> conversation, List<ToolSpec> tools) {
        Message message;
        try {
            message = client.messages().create(params(system, conversation, tools));
        } catch (UnauthorizedException | PermissionDeniedException e) {
            throw new ModelFailed("A chave da IA foi recusada. Fale com o administrador.", e);
        } catch (RateLimitException | AnthropicIoException e) {
            throw new ModelFailed("A IA está ocupada ou fora do ar agora. Tente de novo em alguns minutos.", e);
        } catch (AnthropicServiceException e) {
            throw new ModelFailed(
                    "A IA respondeu com erro (" + e.statusCode() + "). Tente de novo em alguns minutos.", e);
        }
        if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
            throw new ModelFailed("A IA não aceitou responder esta pergunta.", null);
        }
        StringBuilder text = new StringBuilder();
        List<ToolCall> calls = new ArrayList<>();
        for (ContentBlock block : message.content()) {
            block.text().ifPresent(t -> text.append(t.text()));
            block.toolUse().ifPresent(t -> calls.add(new ToolCall(t.id(), t.name(), input(t._input()))));
        }
        return new Reply(
                new AssistantTurn(text.toString(), calls, message.toParam()),
                message.model().asString(),
                message.usage().inputTokens(),
                message.usage().outputTokens());
    }

    MessageCreateParams params(String system, List<Entry> conversation, List<ToolSpec> tools) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(properties.maxTokens())
                .system(system)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.of(properties.effort()))
                        .build());
        for (ToolSpec spec : tools) {
            Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
            spec.properties().forEach((name, schema) -> props.putAdditionalProperty(name, JsonValue.from(schema)));
            builder.addTool(Tool.builder()
                    .name(spec.name())
                    .description(spec.description())
                    .inputSchema(Tool.InputSchema.builder()
                            .properties(props.build())
                            .required(spec.required())
                            .build())
                    .build());
        }
        for (Entry entry : conversation) {
            switch (entry) {
                case UserText u -> builder.addUserMessage(u.text());
                case AssistantTurn a -> {
                    if (a.nativeMessage() instanceof MessageParam param) {
                        builder.addMessage(param);
                    } else {
                        builder.addAssistantMessageOfContentBlockParams(blocks(a));
                    }
                }
                case ToolResults r ->
                    builder.addUserMessageOfContentBlockParams(r.results().stream()
                            .map(res -> ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                                    .toolUseId(res.callId())
                                    .content(res.content())
                                    .isError(res.error())
                                    .build()))
                            .toList());
            }
        }
        return builder.build();
    }

    /** Turno sem a mensagem original (conversa anterior, testes): texto e pedidos de consulta remontados. */
    private static List<ContentBlockParam> blocks(AssistantTurn turn) {
        List<ContentBlockParam> blocks = new ArrayList<>();
        if (turn.text() != null && !turn.text().isBlank()) {
            blocks.add(ContentBlockParam.ofText(turn.text()));
        }
        for (ToolCall call : turn.calls()) {
            ToolUseBlockParam.Input.Builder input = ToolUseBlockParam.Input.builder();
            call.input().forEach((k, v) -> input.putAdditionalProperty(k, JsonValue.from(v)));
            blocks.add(ContentBlockParam.ofToolUse(ToolUseBlockParam.builder()
                    .id(call.id())
                    .name(call.name())
                    .input(input.build())
                    .build()));
        }
        if (blocks.isEmpty()) {
            blocks.add(ContentBlockParam.ofText("(sem texto)"));
        }
        return blocks;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> input(JsonValue value) {
        try {
            Map<String, Object> map = value.convert(Map.class);
            return map == null ? Map.of() : map;
        } catch (RuntimeException e) {
            return Map.of();
        }
    }
}
