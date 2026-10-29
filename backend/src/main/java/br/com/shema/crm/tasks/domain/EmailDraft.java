package br.com.shema.crm.tasks.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Rascunho de e-mail ao órgão, gerado por um alerta (seção 5.4). O sistema nunca envia: a pessoa copia ou abre no
 * cliente de e-mail, revisa e manda.
 *
 * @param to destinatários separados por vírgula; vazio quando o órgão não tem contato com e-mail
 */
@Embeddable
public record EmailDraft(
        @Column(name = "email_to", length = 500) String to,
        @Column(name = "email_subject", length = 200) String subject,
        @Column(name = "email_body", length = 4000) String body) {

    public EmailDraft {
        if (subject == null || subject.isBlank() || body == null || body.isBlank()) {
            throw new IllegalArgumentException("Rascunho precisa de assunto e texto");
        }
        to = to == null || to.isBlank() ? null : truncate(to.trim(), 500);
        subject = truncate(subject.trim(), 200);
        body = truncate(body.strip(), 4000);
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
