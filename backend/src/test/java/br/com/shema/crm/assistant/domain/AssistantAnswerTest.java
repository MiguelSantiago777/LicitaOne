package br.com.shema.crm.assistant.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.shema.crm.assistant.domain.AssistantAnswer.QueryLog;
import br.com.shema.crm.assistant.domain.AssistantAnswer.Ref;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssistantAnswerTest {

    private static Ref ref(String id) {
        return new Ref("CONTRATO", id, "Contrato " + id, "saldo R$ 10,00", "/licitacoes/x", Map.of("aba", "itens"));
    }

    @Test
    void onlyIdsReturnedByTheQueriesBecomeLinksInTheOrderTheModelChose() {
        Map<String, Ref> seen = new LinkedHashMap<>();
        seen.put("a", ref("a"));
        seen.put("b", ref("b"));
        seen.put("c", ref("c"));

        AssistantAnswer answer = AssistantAnswer.of(
                "  Dois contratos.  ",
                List.of("c", "inventado", "a", "c", " b "),
                seen,
                List.of(new QueryLog("Contratos abertos", "sem filtro", 3)),
                List.of());

        assertThat(answer.text()).isEqualTo("Dois contratos.");
        assertThat(answer.items()).extracting(Ref::id).containsExactly("c", "a", "b");
        assertThat(answer.warnings())
                .containsExactly("Um item citado pela IA não veio das consultas e ficou fora da lista.");
        assertThat(answer.queries()).hasSize(1);
    }

    @Test
    void emptyTextLongTextAndTooManyItemsAreHandled() {
        Map<String, Ref> seen = new LinkedHashMap<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            seen.put("id" + i, ref("id" + i));
            ids.add("id" + i);
        }
        AssistantAnswer empty = AssistantAnswer.of(" ", ids, seen, List.of(), List.of("nota"));
        assertThat(empty.text()).startsWith("Não consegui montar a resposta");
        assertThat(empty.items()).hasSize(AssistantAnswer.MAX_ITEMS);
        assertThat(empty.warnings()).containsExactly("nota");

        AssistantAnswer longText = AssistantAnswer.of("x".repeat(5000), List.of(), seen, List.of(), List.of());
        assertThat(longText.text()).hasSize(AssistantAnswer.MAX_TEXT + 1).endsWith("…");
    }
}
