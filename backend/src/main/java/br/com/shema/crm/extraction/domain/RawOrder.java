package br.com.shema.crm.extraction.domain;

import br.com.shema.crm.extraction.domain.RawReading.RawField;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * O que o modelo devolve ao ler um pedido: o esquema da saída estruturada. Como em {@link RawReading}, tudo é texto,
 * com {@code ""} para "não encontrado", e a conversão e a conferência ficam em {@link OrderReadingResult#from}.
 */
public record RawOrder(
        @JsonPropertyDescription(
                "Tipo do documento: OFICIO, AUTORIZACAO_FORNECIMENTO (AF ou ordem de fornecimento), EMPENHO (nota de"
                        + " empenho), EMAIL, MENSAGEM (WhatsApp ou outra mensagem) ou OUTRO")
        String documentType,

        @JsonPropertyDescription("Data do pedido (emissão do ofício, da AF ou do empenho), no formato AAAA-MM-DD")
        RawField orderedAt,

        @JsonPropertyDescription("Número do ofício, da AF, da ordem ou do empenho, como escrito (ex.: AF 123/2026)")
        RawField reference,

        @JsonPropertyDescription("Órgão que faz o pedido, como escrito")
        RawField agencyName,

        @JsonPropertyDescription("Número da ata, do contrato ou do processo citado no pedido, como escrito, ou \"\"")
        RawField contractReference,

        @JsonPropertyDescription("Local, prazo ou condições de entrega, resumidos em uma frase, ou \"\"")
        RawField deliveryNotes,

        @JsonPropertyDescription("Cada item pedido, na ordem do documento")
        List<RawOrderLine> lines,

        @JsonPropertyDescription("Avisos curtos em português sobre o que ficou ilegível, ambíguo ou incompleto")
        List<String> warnings) {

    public record RawOrderLine(
            @JsonPropertyDescription(
                    "Número do item do contrato (da lista enviada, como \"3\") que corresponde a esta linha, ou \"\" se"
                            + " nenhum corresponde com segurança")
            String contractItem,

            @JsonPropertyDescription("Descrição do item como está no pedido")
            String description,

            @JsonPropertyDescription("Unidade como está no pedido (UN, M², CX...), ou \"\"")
            String unit,

            @JsonPropertyDescription("Quantidade pedida, só dígitos e ponto decimal")
            String quantity,

            @JsonPropertyDescription("Preço unitário escrito no pedido, só dígitos e ponto decimal, ou \"\"")
            String unitPrice,

            @JsonPropertyDescription(
                    "Página do documento onde está a linha (1 = primeira); 0 se o pedido é uma imagem ou texto")
            int page,

            @JsonPropertyDescription("Trecho curto do pedido de onde a linha saiu, copiado como está")
            String excerpt) {}
}
