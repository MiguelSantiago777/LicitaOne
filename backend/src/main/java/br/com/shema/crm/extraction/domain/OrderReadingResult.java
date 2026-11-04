package br.com.shema.crm.extraction.domain;

import br.com.shema.crm.extraction.domain.RawOrder.RawOrderLine;
import br.com.shema.crm.extraction.domain.ReadingResult.Value;
import br.com.shema.crm.shared.domain.BrFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pedido lido e conferido contra o contrato: cada linha relacionada a um item (ou não), com quantidade, preço e saldo,
 * e os avisos do que a pessoa precisa olhar antes de registrar. Nada é gravado aqui; o formulário de pedido abre com
 * estes valores e o saldo continua validado pelo contrato ao salvar.
 *
 * @param warnings avisos do modelo e da conferência sobre o pedido como um todo (órgão, contrato citado, data)
 */
@Schema(name = "OrderReadingResult")
public record OrderReadingResult(
        String documentType,
        Value<LocalDate> orderedAt,
        Value<String> reference,
        Value<String> agencyName,
        Value<String> contractReference,
        Value<String> deliveryNotes,
        List<Line> lines,
        List<String> warnings) {

    /**
     * Uma linha do pedido.
     *
     * @param itemId item do contrato relacionado, ou nulo se nenhum corresponde (a linha fica fora do pedido)
     * @param itemNumber posição do item no contrato (1 = primeiro), como foi mostrado ao modelo
     * @param unitPrice preço escrito no pedido, só para comparar: o pedido usa sempre o preço do contrato
     * @param issues o que conferir nesta linha (sem item, acima do saldo, preço diferente)
     */
    @Schema(name = "OrderReadingLine")
    public record Line(
            UUID itemId,
            Integer itemNumber,
            String description,
            String unit,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal contractUnitPrice,
            BigDecimal remaining,
            Integer page,
            String excerpt,
            List<String> issues) {}

    /** O contrato contra o qual o pedido é conferido. */
    public record Contract(String agencyName, String number, LocalDate signedAt, List<Item> items) {

        public Contract {
            items = List.copyOf(items);
        }
    }

    public record Item(UUID id, String description, BigDecimal unitPrice, BigDecimal remaining) {}

    /** Palavras comuns em nome de órgão, que não ajudam a dizer se dois nomes são o mesmo órgão. */
    private static final Set<String> GENERIC = Set.of(
            "prefeitura",
            "municipal",
            "municipio",
            "camara",
            "secretaria",
            "fundo",
            "estado",
            "estadual",
            "governo",
            "fundacao",
            "instituto",
            "servico",
            "servicos",
            "autarquia",
            "publico",
            "publica",
            "saude",
            "educacao",
            "assistencia",
            "social");

    public static OrderReadingResult from(RawOrder raw, Contract contract, LocalDate today) {
        List<String> warnings = new ArrayList<>();
        if (raw.warnings() != null) {
            raw.warnings().stream()
                    .filter(w -> w != null && !w.isBlank())
                    .map(String::trim)
                    .forEach(warnings::add);
        }
        Value<LocalDate> orderedAt = ReadingResult.date(raw.orderedAt(), "a data do pedido", warnings);
        Value<String> reference = ReadingResult.text(raw.reference(), 60);
        Value<String> agencyName = ReadingResult.text(raw.agencyName(), 200);
        Value<String> contractReference = ReadingResult.text(raw.contractReference(), 60);
        Value<String> deliveryNotes = ReadingResult.text(raw.deliveryNotes(), 500);

        LocalDate date = orderedAt.value();
        if (date != null && date.isAfter(today)) {
            warnings.add("A data lida (" + BrFormat.date(date) + ") está no futuro. Confira a data do pedido.");
        } else if (date != null && contract.signedAt() != null && date.isBefore(contract.signedAt())) {
            warnings.add("A data lida (" + BrFormat.date(date) + ") é anterior à assinatura do contrato ("
                    + BrFormat.date(contract.signedAt()) + "). Confira se o pedido é deste contrato.");
        }
        if (agencyName.value() != null
                && contract.agencyName() != null
                && !sameAgency(agencyName.value(), contract.agencyName())) {
            warnings.add("O pedido é de \"" + agencyName.value() + "\" e este contrato é de " + contract.agencyName()
                    + ". Confira se o pedido é deste contrato.");
        }
        if (contractReference.value() != null
                && contract.number() != null
                && !digits(contractReference.value()).contains(digits(contract.number()))) {
            warnings.add("O pedido cita \"" + contractReference.value() + "\" e este contrato é o " + contract.number()
                    + ". Confira se o pedido é deste contrato.");
        }

        List<Line> lines = lines(raw.lines(), contract, warnings);
        if (lines.isEmpty()) {
            warnings.add("A IA não encontrou itens pedidos. Preencha as quantidades à mão.");
        }
        return new OrderReadingResult(
                documentType(raw.documentType()),
                orderedAt,
                reference,
                agencyName,
                contractReference,
                deliveryNotes,
                List.copyOf(lines),
                List.copyOf(warnings));
    }

    private static List<Line> lines(List<RawOrderLine> rawLines, Contract contract, List<String> warnings) {
        List<Line> lines = new ArrayList<>();
        Map<UUID, Integer> byItem = new HashMap<>();
        List<RawOrderLine> source = rawLines == null ? List.of() : rawLines;
        for (int i = 0; i < source.size(); i++) {
            RawOrderLine raw = source.get(i);
            if (raw == null) {
                continue;
            }
            String description = ReadingResult.blankToNull(raw.description(), 500);
            BigDecimal quantity = ReadingResult.number(raw.quantity(), 3);
            if (quantity != null && quantity.signum() <= 0) {
                quantity = null;
            }
            if (description == null && quantity == null) {
                continue;
            }
            Integer number = itemNumber(raw.contractItem(), contract.items().size());
            Item item = number == null ? null : contract.items().get(number - 1);
            Integer previous = item == null ? null : byItem.get(item.id());
            if (previous != null) {
                // O mesmo item em duas linhas: junta na primeira, que é a que vira linha do pedido
                Line first = lines.get(previous);
                BigDecimal sum = first.quantity() == null || quantity == null
                        ? first.quantity()
                        : first.quantity().add(quantity);
                lines.set(previous, check(first, sum, item));
                warnings.add("\"" + item.description() + "\" aparece em mais de uma linha do pedido; as quantidades"
                        + " foram somadas.");
                continue;
            }
            BigDecimal unitPrice = ReadingResult.number(raw.unitPrice(), 4);
            Line line = new Line(
                    item == null ? null : item.id(),
                    number,
                    description != null ? description : item == null ? "Linha " + (i + 1) : item.description(),
                    ReadingResult.blankToNull(raw.unit(), 20),
                    quantity,
                    unitPrice != null && unitPrice.signum() > 0 ? unitPrice : null,
                    item == null ? null : item.unitPrice(),
                    item == null ? null : item.remaining(),
                    ReadingResult.page(raw.page()),
                    ReadingResult.blankToNull(raw.excerpt(), 300),
                    List.of());
            if (item != null) {
                byItem.put(item.id(), lines.size());
            }
            lines.add(check(line, quantity, item));
        }
        return lines;
    }

    /** Refaz os avisos da linha com a quantidade final. */
    private static Line check(Line line, BigDecimal quantity, Item item) {
        List<String> issues = new ArrayList<>();
        if (item == null) {
            issues.add("Não corresponde a nenhum item deste contrato; fica fora do pedido.");
        } else {
            if (quantity == null) {
                issues.add("Não deu para ler a quantidade.");
            } else if (quantity.compareTo(item.remaining()) > 0) {
                issues.add("O pedido é de " + BrFormat.quantity(quantity) + " e o saldo é de "
                        + BrFormat.quantity(item.remaining()) + ".");
            }
            if (line.unitPrice() != null
                    && line.unitPrice()
                                    .setScale(2, RoundingMode.HALF_UP)
                                    .compareTo(item.unitPrice().setScale(2, RoundingMode.HALF_UP))
                            != 0) {
                issues.add("O pedido diz " + BrFormat.money(line.unitPrice()) + " por unidade e o contrato, "
                        + BrFormat.money(item.unitPrice()) + ". O pedido usa o preço do contrato.");
            }
        }
        return new Line(
                line.itemId(),
                line.itemNumber(),
                line.description(),
                line.unit(),
                quantity,
                line.unitPrice(),
                line.contractUnitPrice(),
                line.remaining(),
                line.page(),
                line.excerpt(),
                List.copyOf(issues));
    }

    private static Integer itemNumber(String value, int size) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceFirst("(?i)^item\\s*", "");
        if (!trimmed.matches("\\d{1,4}")) {
            return null;
        }
        int n = Integer.parseInt(trimmed);
        return n >= 1 && n <= size ? n : null;
    }

    /**
     * Mesmo órgão se uma palavra significativa (sem acento, com 4 letras ou mais, fora as genéricas como "prefeitura")
     * de um nome aparece no outro. Sem palavra significativa, não dá para dizer, e não avisa.
     */
    static boolean sameAgency(String read, String expected) {
        Set<String> expectedWords = significant(expected);
        Set<String> readWords = significant(read);
        if (expectedWords.isEmpty() || readWords.isEmpty()) {
            return true;
        }
        return expectedWords.stream().anyMatch(readWords::contains);
    }

    private static Set<String> significant(String name) {
        return Set.copyOf(Arrays.stream(ReadingResult.plain(name).split("[^a-z0-9]+"))
                .filter(w -> w.length() >= 4 && !GENERIC.contains(w))
                .toList());
    }

    private static String digits(String value) {
        return value.replaceAll("\\D", "");
    }

    private static String documentType(String value) {
        String type = value == null ? "" : ReadingResult.plain(value).toUpperCase(Locale.ROOT);
        return switch (type) {
            case "OFICIO", "AUTORIZACAO_FORNECIMENTO", "EMPENHO", "EMAIL", "MENSAGEM" -> type;
            default -> "OUTRO";
        };
    }
}
