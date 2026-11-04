package br.com.shema.crm.extraction.domain;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** Rascunho de follow-up escrito pelo modelo (Fase 9), conferido em {@link FollowUpDraft#from}. */
public record RawFollowUp(
        @JsonPropertyDescription("Assunto do e-mail; \"\" para WhatsApp")
        String subject,

        @JsonPropertyDescription("Texto da mensagem, pronto para a pessoa revisar e mandar")
        String body,

        @JsonPropertyDescription(
                "Avisos curtos para quem vai mandar (ex.: \"Não há contato registrado; confirme o nome do"
                        + " destinatário.\")")
        List<String> warnings) {}
