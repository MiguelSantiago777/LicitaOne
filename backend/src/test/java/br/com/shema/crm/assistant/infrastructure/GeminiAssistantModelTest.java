package br.com.shema.crm.assistant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.shema.crm.assistant.application.AssistantModel.AssistantTurn;
import br.com.shema.crm.assistant.application.AssistantModel.Entry;
import br.com.shema.crm.assistant.application.AssistantModel.Reply;
import br.com.shema.crm.assistant.application.AssistantModel.ToolResult;
import br.com.shema.crm.assistant.application.AssistantModel.ToolResults;
import br.com.shema.crm.assistant.application.AssistantModel.ToolSpec;
import br.com.shema.crm.assistant.application.AssistantModel.UserText;
import br.com.shema.crm.assistant.application.AssistantProperties;
import br.com.shema.crm.shared.ai.FakeGemini;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** O assistente com o Gemini contra uma API falsa: declarações das consultas, chamadas e respostas de volta. */
class GeminiAssistantModelTest {

    private static final AssistantProperties PROPERTIES = new AssistantProperties(
            null, "claude-opus-5-5", "medium", 8000, 50, 6, 10, false, "gemini", "chave-de-teste", "gemini-3.8-flash");

    private static final List<ToolSpec> TOOLS = List.of(new ToolSpec(
            "buscar_licitacoes",
            "Busca licitações",
            Map.of("texto", Map.of("type", "string", "description", "Trecho do objeto")),
            List.of("texto")));

    private final FakeGemini api = new FakeGemini();
    private final GeminiAssistantModel model = new GeminiAssistantModel(api.client(), PROPERTIES);

    @AfterEach
    void close() {
        api.close();
    }

    @Test
    void aFunctionCallComesBackAsAToolCallAndItsContentGoesBackUntouched() {
        api.reply(200, """
                {"candidates":[{"finishReason":"STOP","content":{"role":"model","parts":[
                  {"functionCall":{"name":"buscar_licitacoes","args":{"texto":"caneta"}},"thoughtSignature":"abc"}]}}],
                 "usageMetadata":{"promptTokenCount":500,"candidatesTokenCount":20},"modelVersion":"gemini-3.8-flash"}
                """);
        api.reply(200, """
                {"candidates":[{"finishReason":"STOP","content":{"role":"model","parts":[{"text":"Achei uma."}]}}],
                 "usageMetadata":{"promptTokenCount":700,"candidatesTokenCount":10}}
                """);

        List<Entry> conversation = new ArrayList<>(List.of(new UserText("Tem licitação de caneta?")));
        Reply first = model.next("sistema", conversation, TOOLS);

        assertThat(first.turn().calls()).singleElement().satisfies(call -> {
            assertThat(call.name()).isEqualTo("buscar_licitacoes");
            assertThat(call.input()).containsEntry("texto", "caneta");
        });
        assertThat(first.inputTokens()).isEqualTo(500);

        conversation.add(first.turn());
        conversation.add(new ToolResults(
                List.of(new ToolResult(first.turn().calls().getFirst().id(), "[{\"id\":\"1\"}]", false))));
        Reply second = model.next("sistema", conversation, TOOLS);

        assertThat(second.turn().text()).isEqualTo("Achei uma.");
        assertThat(second.turn().calls()).isEmpty();

        JsonNode body = api.requests().get(1);
        assertThat(body.at("/tools/0/functionDeclarations/0/name").asString()).isEqualTo("buscar_licitacoes");
        assertThat(body.at("/tools/0/functionDeclarations/0/parametersJsonSchema/required/0")
                        .asString())
                .isEqualTo("texto");
        assertThat(body.at("/contents/1/parts/0/thoughtSignature").asString()).isEqualTo("abc");
        JsonNode response = body.at("/contents/2/parts/0/functionResponse");
        assertThat(response.path("name").asString()).isEqualTo("buscar_licitacoes");
        assertThat(response.at("/response/result").asString()).contains("\"id\"");
    }

    @Test
    void anOldAnswerWithoutTheOriginalContentIsRebuiltAsText() {
        api.replyText("Ok.");

        model.next(
                "sistema",
                List.of(new UserText("Oi"), new AssistantTurn("Olá!", List.of(), null), new UserText("E aí?")),
                TOOLS);

        JsonNode contents = api.requests().getFirst().path("contents");
        assertThat(contents.at("/1/role").asString()).isEqualTo("model");
        assertThat(contents.at("/1/parts/0/text").asString()).isEqualTo("Olá!");
        assertThat(contents.at("/2/parts/0/text").asString()).isEqualTo("E aí?");
    }
}
