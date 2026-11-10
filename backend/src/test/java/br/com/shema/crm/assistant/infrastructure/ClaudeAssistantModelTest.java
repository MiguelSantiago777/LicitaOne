package br.com.shema.crm.assistant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.shema.crm.assistant.application.AssistantModel.AssistantTurn;
import br.com.shema.crm.assistant.application.AssistantModel.ToolCall;
import br.com.shema.crm.assistant.application.AssistantModel.ToolResult;
import br.com.shema.crm.assistant.application.AssistantModel.ToolResults;
import br.com.shema.crm.assistant.application.AssistantModel.ToolSpec;
import br.com.shema.crm.assistant.application.AssistantModel.UserText;
import br.com.shema.crm.assistant.application.AssistantProperties;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Monta a requisição sem chamar a API: ferramentas, raciocínio adaptativo e a volta dos resultados das consultas. */
class ClaudeAssistantModelTest {

    private static final AssistantProperties PROPERTIES = new AssistantProperties(
            "chave-de-teste", "claude-opus-5-5", "medium", 8000, 50, 6, 10, false, "claude", null, "gemini-3.8-flash");

    private final ClaudeAssistantModel model = new ClaudeAssistantModel(
            AnthropicOkHttpClient.builder().apiKey("chave-de-teste").build(), PROPERTIES);

    @Test
    void sendsTheToolsTheHistoryAndTheToolResults() {
        ToolSpec tool = new ToolSpec(
                "contratos_abertos",
                "Contratos abertos",
                Map.of("saldo_minimo", Map.of("type", "number", "description", "Saldo mínimo")),
                List.of());

        MessageCreateParams params = model.params(
                "Você é o assistente.",
                List.of(
                        new UserText("Quantos contratos abertos?"),
                        new AssistantTurn("Três.", List.of(), null),
                        new UserText("E com saldo acima de 100 mil?"),
                        new AssistantTurn(
                                "",
                                List.of(new ToolCall("call-1", "contratos_abertos", Map.of("saldo_minimo", 100000))),
                                null),
                        new ToolResults(List.of(new ToolResult("call-1", "{\"total\":1}", false)))),
                List.of(tool));

        assertThat(params.model().asString()).isEqualTo("claude-opus-5-5");
        assertThat(params.system().orElseThrow().asString()).isEqualTo("Você é o assistente.");
        assertThat(params.thinking()).isPresent();
        assertThat(params.tools().orElseThrow())
                .singleElement()
                .satisfies(t -> assertThat(t.asTool().name()).isEqualTo("contratos_abertos"));
        var messages = params.messages();
        assertThat(messages).hasSize(5);
        var results = messages.getLast().content().asContentBlockParams();
        assertThat(results.getFirst().isToolResult()).isTrue();
        assertThat(results.getFirst().asToolResult().toolUseId()).isEqualTo("call-1");
    }
}
