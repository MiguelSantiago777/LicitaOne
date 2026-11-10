package br.com.shema.crm.assistant.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Schema(name = "AssistantAnswerResponse")
public record AssistantAnswer(String text, List<Ref> items, List<QueryLog> queries, List<String> warnings) {

    /** Teto do texto da resposta: o painel é estreito. */
    public static final int MAX_TEXT = 3000;

    /** Teto da lista clicável. */
    public static final int MAX_ITEMS = 30;

    /**
     * Um item da lista clicável. {@code path} e {@code query} são a rota da tela no front.
     *
     * @param type LICITACAO, CONTRATO, ATA, ORGAO, TAREFA ou NEGOCIACAO
     * @param detail um dado-chave para a linha (saldo, vencimento, situação)
     */
    @Schema(name = "AssistantItemResponse")
    public record Ref(String type, String id, String label, String detail, String path, Map<String, String> query) {

        public Ref {
            query = query == null ? Map.of() : Map.copyOf(query);
        }
    }

    @Schema(name = "AssistantQueryResponse")
    public record QueryLog(String label, String filters, int rows) {}

    public static AssistantAnswer of(
            String text, List<String> chosenIds, Map<String, Ref> seen, List<QueryLog> queries, List<String> notes) {
        List<String> warnings = new ArrayList<>(notes);
        String body = text == null ? "" : text.strip();
        if (body.isEmpty()) {
            body = "Não consegui montar a resposta. Tente perguntar de outro jeito.";
        }
        if (body.length() > MAX_TEXT) {
            body = body.substring(0, MAX_TEXT) + "…";
        }
        Map<String, Ref> chosen = new LinkedHashMap<>();
        int unknown = 0;
        for (String id : chosenIds == null ? List.<String>of() : chosenIds) {
            Ref ref = id == null ? null : seen.get(id.strip());
            if (ref == null) {
                unknown++;
            } else if (chosen.size() < MAX_ITEMS) {
                chosen.putIfAbsent(ref.id(), ref);
            }
        }
        if (unknown > 0) {
            warnings.add(
                    unknown == 1
                            ? "Um item citado pela IA não veio das consultas e ficou fora da lista."
                            : unknown + " itens citados pela IA não vieram das consultas e ficaram fora da lista.");
        }
        return new AssistantAnswer(body, List.copyOf(chosen.values()), List.copyOf(queries), List.copyOf(warnings));
    }
}
