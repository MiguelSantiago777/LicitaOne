package br.com.shema.crm.extraction.domain;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * O que o modelo devolve: o esquema da saída estruturada. Tudo é texto, com {@code ""} para "não encontrado", porque
 * números e datas são conferidos depois por {@link ReadingResult#from} (dinheiro nunca passa por double).
 */
public record RawReading(
        @JsonPropertyDescription("Tipo do documento: ATA (ata de registro de preços), CONTRATO ou OUTRO")
        String documentType,

        @JsonPropertyDescription("Número da ata ou do contrato, como escrito (ex.: 012/2026)")
        RawField number,

        @JsonPropertyDescription("Data de assinatura, no formato AAAA-MM-DD")
        RawField signedAt,

        @JsonPropertyDescription("Início da vigência, no formato AAAA-MM-DD")
        RawField validFrom,

        @JsonPropertyDescription("Fim da vigência, no formato AAAA-MM-DD")
        RawField validUntil,

        @JsonPropertyDescription(
                "SIM se o documento permite adesão (carona) de órgãos não participantes, NAO se proíbe, \"\" se"
                        + " não diz")
        RawField allowsAdhesion,

        @JsonPropertyDescription("Valor total, só dígitos e ponto decimal (ex.: 300000.00)")
        RawField totalValue,

        @JsonPropertyDescription("Órgão gerenciador ou contratante")
        RawField agencyName,

        @JsonPropertyDescription("Fornecedor dos itens listados (a empresa detentora da ata ou contratada)")
        RawField supplierName,

        @JsonPropertyDescription("Itens registrados ou contratados do fornecedor, na ordem do documento")
        List<RawItem> items,

        @JsonPropertyDescription("Avisos curtos em português sobre o que ficou ilegível, ambíguo ou incompleto")
        List<String> warnings) {

    /** Um campo lido e a página (1 = primeira; 0 = não sabe) de onde saiu. */
    public record RawField(
            @JsonPropertyDescription("Valor encontrado, ou \"\" se o documento não traz")
            String value,

            @JsonPropertyDescription("Página do PDF onde está o valor (1 = primeira); 0 se não encontrado")
            int page) {}

    public record RawItem(
            @JsonPropertyDescription("Lote ou grupo, se houver; senão \"\"")
            String lot,

            @JsonPropertyDescription("Descrição do item, como no documento")
            String description,

            @JsonPropertyDescription("Unidade de fornecimento (UN, M², CX...), ou \"\"")
            String unit,

            @JsonPropertyDescription("Quantidade registrada ou contratada, só dígitos e ponto decimal")
            String quantity,

            @JsonPropertyDescription("Preço unitário, só dígitos e ponto decimal (ex.: 12.50)")
            String unitPrice,

            @JsonPropertyDescription("Página do PDF onde está o item (1 = primeira)")
            int page) {}
}
