package br.com.shema.crm.extraction.domain;

import br.com.shema.crm.extraction.domain.RawReading.RawField;
import br.com.shema.crm.shared.domain.BrFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A resposta do órgão entendida e conferida (Fase 9): situação, data prevista, resumo e a tarefa sugerida. Nada é
 * gravado aqui; o diálogo de contato abre com estes valores e a pessoa confere antes de salvar.
 *
 * @param outcome um dos valores de situação do contato (PEDIDO_PREVISTO, SEM_PREVISAO...), ou OUTRO
 * @param expectedDate data prevista do pedido, se a resposta der uma
 * @param taskDueDate quando fazer a próxima ação; nunca antes de hoje
 */
@Schema(name = "ResponseReading")
public record ResponseReading(
        String outcome,
        LocalDate expectedDate,
        boolean expectedDateIsVague,
        String summary,
        String taskTitle,
        LocalDate taskDueDate,
        List<String> warnings) {

    private static final Set<String> OUTCOMES =
            Set.of("PEDIDO_PREVISTO", "SEM_PREVISAO", "PEDIDO_A_CAMINHO", "NAO_VAI_PEDIR", "PEDE_DOCUMENTO", "OUTRO");

    public static ResponseReading from(RawResponse raw, LocalDate contactedAt, LocalDate today) {
        List<String> warnings = new ArrayList<>();
        if (raw.warnings() != null) {
            raw.warnings().stream()
                    .filter(w -> w != null && !w.isBlank())
                    .map(String::trim)
                    .forEach(warnings::add);
        }
        String outcome = raw.outcome() == null ? "" : raw.outcome().trim().toUpperCase(Locale.ROOT);
        if (!OUTCOMES.contains(outcome)) {
            outcome = "OUTRO";
        }

        LocalDate expected = ReadingResult.date(new RawField(raw.expectedDate(), 0), "a data prevista", warnings)
                .value();
        if (expected != null && expected.isBefore(contactedAt)) {
            warnings.add("A data prevista lida (" + BrFormat.date(expected) + ") é anterior ao contato. Confira.");
            expected = null;
        }
        boolean vague = expected != null && raw.expectedDateIsVague();
        if (vague) {
            warnings.add("A resposta não dá uma data exata; a data prevista é uma estimativa.");
        }
        if ("PEDIDO_PREVISTO".equals(outcome) && expected == null) {
            warnings.add("A resposta fala em pedido, mas sem data. Combine uma data para cobrar.");
        }

        String title = ReadingResult.blankToNull(raw.taskTitle(), 200);
        LocalDate due = ReadingResult.date(new RawField(raw.taskDueDate(), 0), "a data da tarefa", warnings)
                .value();
        if (title != null && due == null) {
            due = expected != null ? expected : today.plusDays(7);
        }
        if (due != null && due.isBefore(today)) {
            due = today;
        }
        if (title == null) {
            due = null;
        }
        String summary = ReadingResult.blankToNull(raw.summary(), 1000);
        return new ResponseReading(
                outcome, expected, vague, summary == null ? "" : summary, title, due, List.copyOf(warnings));
    }
}
