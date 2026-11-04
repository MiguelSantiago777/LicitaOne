package br.com.shema.crm.extraction.domain;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * O que o modelo escreve no kit de carona (Fase 10): a carta de apresentação e a justificativa do ofício de
 * solicitação. O resto do kit vem de modelos fixos ({@link CaronaLetters}).
 */
public record RawCaronaKit(
        @JsonPropertyDescription("Assunto do e-mail da carta de apresentação")
        String presentationSubject,

        @JsonPropertyDescription("Carta de apresentação ao município, pronta para revisar, sem cabeçalho da empresa")
        String presentationBody,

        @JsonPropertyDescription(
                "Parágrafo de justificativa da vantagem da adesão, escrito como o município, para o ofício de"
                        + " solicitação")
        String requestJustification,

        @JsonPropertyDescription("Avisos curtos para quem vai mandar o kit")
        List<String> warnings) {}
