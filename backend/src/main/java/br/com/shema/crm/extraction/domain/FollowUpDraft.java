package br.com.shema.crm.extraction.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * Rascunho de follow-up conferido (Fase 9). O sistema nunca envia: a pessoa revisa, copia ou abre no e-mail ou no
 * WhatsApp.
 *
 * @param subject assunto do e-mail; nulo no WhatsApp
 */
@Schema(name = "FollowUpDraft")
public record FollowUpDraft(String subject, String body, List<String> warnings) {

    /** Mesmo teto do rascunho de e-mail das tarefas. */
    public static final int MAX_BODY = 4000;

    /** WhatsApp é curto: acima disso, avisa. */
    static final int WHATSAPP_LONG = 700;

    public static FollowUpDraft from(RawFollowUp raw, boolean whatsapp) {
        List<String> warnings = new ArrayList<>();
        if (raw.warnings() != null) {
            raw.warnings().stream()
                    .filter(w -> w != null && !w.isBlank())
                    .map(String::trim)
                    .forEach(warnings::add);
        }
        String body = raw.body() == null ? "" : raw.body().strip();
        if (body.isEmpty()) {
            throw new IllegalArgumentException("A IA não escreveu o texto.");
        }
        if (body.length() > MAX_BODY) {
            body = body.substring(0, MAX_BODY);
            warnings.add("O texto foi cortado em 4.000 caracteres.");
        }
        if (whatsapp && body.length() > WHATSAPP_LONG) {
            warnings.add("O texto ficou longo para WhatsApp. Peça \"mais curto\" ou corte antes de mandar.");
        }
        String subject = whatsapp ? null : ReadingResult.blankToNull(raw.subject(), 200);
        if (!whatsapp && subject == null) {
            subject = "Previsão de pedidos";
        }
        return new FollowUpDraft(subject, body, List.copyOf(warnings));
    }
}
