package br.com.shema.crm.assistant.application;

import java.util.List;
import java.util.Map;

/**
 * O modelo que conversa e escolhe as consultas. A implementação real chama o Claude; a de demonstração responde por
 * palavra-chave (E2E); os testes usam uma roteirizada. O laço (executar as consultas, conferir os ids, limitar as
 * rodadas) fica em {@link AssistantService}, igual para todas.
 */
public interface AssistantModel {

    /**
     * Uma consulta que o modelo pode pedir.
     *
     * @param properties esquema JSON de cada parâmetro
     */
    record ToolSpec(String name, String description, Map<String, Object> properties, List<String> required) {}

    /** Um trecho da conversa. */
    sealed interface Entry permits UserText, AssistantTurn, ToolResults {}

    record UserText(String text) implements Entry {}

    /**
     * O que o modelo respondeu numa rodada.
     *
     * @param text texto livre (pode vir vazio quando só há consultas)
     * @param nativeMessage a mensagem como o provedor precisa receber de volta (ex.: com o raciocínio); nula para
     *     respostas antigas que voltam só como texto
     */
    record AssistantTurn(String text, List<ToolCall> calls, Object nativeMessage) implements Entry {

        public AssistantTurn {
            calls = List.copyOf(calls);
        }
    }

    record ToolCall(String id, String name, Map<String, Object> input) {}

    record ToolResults(List<ToolResult> results) implements Entry {

        public ToolResults {
            results = List.copyOf(results);
        }
    }

    record ToolResult(String callId, String content, boolean error) {}

    record Reply(AssistantTurn turn, String model, long inputTokens, long outputTokens) {}

    /** A chamada não deu certo por um motivo que a pessoa entende (IA fora do ar, chave recusada). */
    class ModelFailed extends RuntimeException {

        public ModelFailed(String userMessage, Throwable cause) {
            super(userMessage, cause);
        }
    }

    boolean enabled();

    Reply next(String system, List<Entry> conversation, List<ToolSpec> tools);
}
