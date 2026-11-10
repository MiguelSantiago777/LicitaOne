package br.com.shema.crm.assistant.infrastructure;

import br.com.shema.crm.assistant.application.AssistantModel;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Assistente de demonstração, sem IA (E2E e tela sem chave): escolhe uma consulta pela palavra-chave da pergunta e
 * responde com o total e os cinco primeiros itens que ela devolveu. Passa pelo mesmo laço, pelas mesmas consultas e
 * pela mesma conferência dos ids que o modelo real.
 */
class DemoAssistantModel implements AssistantModel {

    static final String MODEL = "demonstracao";

    private final JsonMapper json;

    DemoAssistantModel(JsonMapper json) {
        this.json = json;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Reply next(String system, List<Entry> conversation, List<ToolSpec> tools) {
        Entry last = conversation.getLast();
        if (last instanceof ToolResults results) {
            return answer(results);
        }
        String question = last instanceof UserText u ? plain(u.text()) : "";
        ToolCall call;
        if (question.contains("saldo") || question.contains("pedem") || question.contains("pedido")) {
            call = new ToolCall("demo-1", "contratos_abertos", Map.of());
        } else if (question.contains("municip")) {
            call = new ToolCall("demo-1", "municipios_sem_ata", Map.of("uf", "RJ"));
        } else if (question.contains("ata") || question.contains("carona")) {
            call = new ToolCall("demo-1", "buscar_atas", Map.of("filtro", "OPORTUNIDADES"));
        } else if (question.contains("tarefa") || question.contains("atrasad")) {
            call = new ToolCall("demo-1", "buscar_tarefas", Map.of("situacao", "ABERTAS"));
        } else {
            call = new ToolCall("demo-1", "buscar_licitacoes", Map.of());
        }
        return new Reply(new AssistantTurn("", List.of(call), null), MODEL, 0, 0);
    }

    private Reply answer(ToolResults results) {
        List<String> ids = new ArrayList<>();
        long total = 0;
        for (ToolResult r : results.results()) {
            JsonNode node = json.readTree(r.content());
            for (JsonNode field : node) {
                if (field.isArray()) {
                    total += field.size();
                    for (JsonNode row : field) {
                        if (ids.size() < 5 && row.has("id")) {
                            ids.add(row.get("id").asString());
                        }
                    }
                }
            }
            if (node.has("total")) {
                total = node.get("total").asLong();
            }
        }
        String text = total == 0
                ? "Não encontrei nada com esses filtros (demonstração)."
                : "Encontrei " + total + (total == 1 ? " resultado" : " resultados")
                        + ". Os principais estão na lista abaixo (demonstração).";
        return new Reply(
                new AssistantTurn(
                        "", List.of(new ToolCall("demo-2", "responder", Map.of("texto", text, "itens", ids))), null),
                MODEL,
                0,
                0);
    }

    private static String plain(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
