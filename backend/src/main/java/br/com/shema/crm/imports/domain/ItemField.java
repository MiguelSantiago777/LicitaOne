package br.com.shema.crm.imports.domain;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Colunas que a importação de itens entende, com os nomes de cabeçalho que a equipe usa (sem acento, caixa ou
 * pontuação: "V.U. " vira "v u"). O casamento é pelo nome inteiro, para "PEDIDO V.U." não ser lido como "V.U.".
 */
public enum ItemField {
    LOT("Lote", false, List.of("lote")),
    DESCRIPTION("Descrição", true, List.of("descricao", "descricao do item", "produto", "especificacao", "material")),
    UNIT("Unidade", false, List.of("unidade", "und", "un", "unid", "unidade de medida")),
    QUANTITY(
            "Quantidade",
            true,
            List.of("quantidade", "qtd", "qtde", "quant", "quantidade contratada", "qtd contratada")),
    UNIT_PRICE(
            "Valor unitário",
            true,
            List.of("v u", "vu", "vl unit", "v unit", "valor unitario", "preco unitario", "valor unit")),
    ORDERED_QUANTITY(
            "Quantidade pedida",
            false,
            List.of("pedido qtd", "pedido qtde", "qtd pedida", "quantidade pedida", "pedido quantidade", "pedido"));

    private final String label;
    private final boolean required;
    private final List<String> headers;

    ItemField(String label, boolean required, List<String> headers) {
        this.label = label;
        this.required = required;
        this.headers = headers;
    }

    public String label() {
        return label;
    }

    public boolean required() {
        return required;
    }

    public boolean matchesHeader(String header) {
        return header != null && headers.contains(normalize(header));
    }

    static String normalize(String value) {
        String noAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
