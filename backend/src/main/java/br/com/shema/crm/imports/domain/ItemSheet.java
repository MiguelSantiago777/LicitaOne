package br.com.shema.crm.imports.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interpretação de uma aba de itens: onde está o cabeçalho, que coluna é cada campo e como cada linha vira um item.
 * Feito para a planilha real do cliente: título mesclado no topo, cabeçalho na linha 5, colunas de pedido ao lado das
 * de item e totais soltos abaixo da tabela.
 */
public final class ItemSheet {

    /** Quantas linhas do topo procurar pelo cabeçalho. */
    static final int HEADER_SEARCH_ROWS = 30;

    public record Header(int rowNumber, Map<ItemField, Integer> mapping) {}

    /**
     * Uma linha depois do cabeçalho.
     *
     * @param errors motivos para a linha não entrar; vazio se entrou
     */
    public record ParsedRow(
            int rowNumber,
            String lot,
            String description,
            String unit,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal orderedQuantity,
            List<String> errors) {

        public boolean valid() {
            return errors.isEmpty();
        }
    }

    private ItemSheet() {}

    /** A linha do topo que mais tem cabeçalhos conhecidos, se tiver pelo menos dois. */
    public static Optional<Header> detectHeader(List<SheetRow> rows) {
        Header best = null;
        for (SheetRow row : rows.subList(0, Math.min(rows.size(), HEADER_SEARCH_ROWS))) {
            Map<ItemField, Integer> mapping = new EnumMap<>(ItemField.class);
            for (int c = 0; c < row.cells().size(); c++) {
                String header = row.cell(c);
                for (ItemField field : ItemField.values()) {
                    if (!mapping.containsKey(field) && field.matchesHeader(header)) {
                        mapping.put(field, c);
                        break;
                    }
                }
            }
            if (mapping.size() >= 2
                    && (best == null || mapping.size() > best.mapping().size())) {
                best = new Header(row.number(), mapping);
            }
        }
        return Optional.ofNullable(best);
    }

    /** Campos obrigatórios que o mapeamento não cobre. */
    public static List<ItemField> missingRequired(Map<ItemField, Integer> mapping) {
        List<ItemField> missing = new ArrayList<>();
        for (ItemField field : ItemField.values()) {
            if (field.required() && mapping.get(field) == null) {
                missing.add(field);
            }
        }
        return missing;
    }

    /**
     * As linhas abaixo do cabeçalho. Linhas sem nenhum campo mapeado preenchido (totais, legendas, linhas em branco)
     * são ignoradas sem erro.
     */
    public static List<ParsedRow> parse(List<SheetRow> rows, int headerRow, Map<ItemField, Integer> mapping) {
        List<ParsedRow> parsed = new ArrayList<>();
        for (SheetRow row : rows) {
            if (row.number() <= headerRow) {
                continue;
            }
            boolean anyMapped = mapping.values().stream().anyMatch(c -> c != null && row.cell(c) != null);
            if (!anyMapped) {
                continue;
            }
            parsed.add(parse(row, mapping));
        }
        return parsed;
    }

    private static ParsedRow parse(SheetRow row, Map<ItemField, Integer> mapping) {
        List<String> errors = new ArrayList<>();
        String description = text(row, mapping, ItemField.DESCRIPTION);
        if (description == null) {
            errors.add("Sem descrição.");
        } else if (description.length() > 500) {
            errors.add("Descrição com mais de 500 caracteres.");
        }
        String lot = text(row, mapping, ItemField.LOT);
        if (lot != null && lot.length() > 40) {
            errors.add("Lote com mais de 40 caracteres.");
        }
        String unit = text(row, mapping, ItemField.UNIT);
        if (unit != null && unit.length() > 20) {
            unit = unit.substring(0, 20);
        }
        BigDecimal quantity = number(row, mapping, ItemField.QUANTITY, 3, errors);
        if (quantity != null && quantity.signum() <= 0) {
            errors.add("Quantidade deve ser maior que zero.");
        }
        if (quantity == null && cell(row, mapping, ItemField.QUANTITY) == null) {
            errors.add("Sem quantidade.");
        }
        BigDecimal unitPrice = number(row, mapping, ItemField.UNIT_PRICE, 4, errors);
        if (unitPrice != null && unitPrice.signum() < 0) {
            errors.add("Valor unitário negativo.");
        }
        if (unitPrice == null && cell(row, mapping, ItemField.UNIT_PRICE) == null) {
            errors.add("Sem valor unitário.");
        }
        BigDecimal ordered = number(row, mapping, ItemField.ORDERED_QUANTITY, 3, errors);
        if (ordered != null && ordered.signum() < 0) {
            errors.add("Quantidade pedida negativa.");
        }
        return new ParsedRow(row.number(), lot, description, unit, quantity, unitPrice, ordered, List.copyOf(errors));
    }

    private static String cell(SheetRow row, Map<ItemField, Integer> mapping, ItemField field) {
        Integer column = mapping.get(field);
        return column == null ? null : row.cell(column);
    }

    private static String text(SheetRow row, Map<ItemField, Integer> mapping, ItemField field) {
        String value = cell(row, mapping, field);
        return value == null ? null : value.strip().replaceAll("\\s+", " ");
    }

    private static BigDecimal number(
            SheetRow row, Map<ItemField, Integer> mapping, ItemField field, int scale, List<String> errors) {
        String value = cell(row, mapping, field);
        if (value == null) {
            return null;
        }
        try {
            return parseNumber(value).setScale(scale, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            errors.add(field.label() + " não é um número: \"" + value + "\".");
            return null;
        }
    }

    /**
     * Número como vem da planilha: "4.1" (célula numérica), "1.234,56" ou "R$ 1.234,56" (texto em português),
     * "1234.56" (CSV em inglês).
     */
    static BigDecimal parseNumber(String raw) {
        String value = raw.replace("R$", "")
                .replace(String.valueOf((char) 0x00A0), "")
                .replace(" ", "")
                .trim();
        if (value.contains(",")) {
            value = value.replace(".", "").replace(',', '.');
        } else if (value.chars().filter(ch -> ch == '.').count() > 1) {
            value = value.replace(".", "");
        }
        return new BigDecimal(value);
    }
}
