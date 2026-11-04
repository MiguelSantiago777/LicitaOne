package br.com.shema.crm.extraction.domain;

import br.com.shema.crm.extraction.domain.RawReading.RawField;
import br.com.shema.crm.extraction.domain.RawReading.RawItem;
import br.com.shema.crm.shared.domain.BrFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Sugestão conferida: datas e números já convertidos, com a página de origem de cada valor. Nada aqui é gravado na
 * ata nem no contrato; o formulário abre com estes valores e a pessoa confere antes de salvar.
 *
 * @param warnings avisos do modelo e os da conferência (valor ilegível, vigência invertida, fornecedor diferente)
 */
@Schema(name = "ExtractionReading")
public record ReadingResult(
        String documentType,
        Value<String> number,
        Value<LocalDate> signedAt,
        Value<LocalDate> validFrom,
        Value<LocalDate> validUntil,
        Value<Boolean> allowsAdhesion,
        Value<BigDecimal> totalValue,
        Value<String> agencyName,
        Value<String> supplierName,
        List<Item> items,
        List<String> warnings) {

    /** @param value nulo quando o documento não traz ou o valor não pôde ser lido; {@code page} nulo se não sabe */
    public record Value<T>(T value, Integer page) {

        static <T> Value<T> empty() {
            return new Value<>(null, null);
        }
    }

    @Schema(name = "ExtractionItem")
    public record Item(
            String lot, String description, String unit, BigDecimal quantity, BigDecimal unitPrice, Integer page) {}

    /** Diferença aceita entre a soma dos itens e o valor total lido, antes de avisar (arredondamento do documento). */
    private static final BigDecimal TOTAL_TOLERANCE = new BigDecimal("0.01");

    private static final Pattern DIGITS = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    /**
     * Converte e confere o que o modelo leu.
     *
     * @param companyName empresa dona do sistema: avisa se o fornecedor lido não parece ser ela
     */
    public static ReadingResult from(RawReading raw, String companyName) {
        List<String> warnings = new ArrayList<>();
        if (raw.warnings() != null) {
            raw.warnings().stream()
                    .filter(w -> w != null && !w.isBlank())
                    .map(String::trim)
                    .forEach(warnings::add);
        }
        Value<String> number = text(raw.number(), 60);
        Value<LocalDate> signedAt = date(raw.signedAt(), "a data de assinatura", warnings);
        Value<LocalDate> validFrom = date(raw.validFrom(), "o início da vigência", warnings);
        Value<LocalDate> validUntil = date(raw.validUntil(), "o fim da vigência", warnings);
        Value<Boolean> allowsAdhesion = yesNo(raw.allowsAdhesion());
        Value<BigDecimal> totalValue = money(raw.totalValue(), warnings);
        Value<String> agencyName = text(raw.agencyName(), 200);
        Value<String> supplierName = text(raw.supplierName(), 200);

        LocalDate start = signedAt.value() != null ? signedAt.value() : validFrom.value();
        if (start != null && validUntil.value() != null && validUntil.value().isBefore(start)) {
            warnings.add("O fim da vigência lido (" + BrFormat.date(validUntil.value())
                    + ") é anterior ao início. Confira as datas.");
        }
        if (supplierName.value() != null && !sameCompany(supplierName.value(), companyName)) {
            warnings.add("O fornecedor lido é \"" + supplierName.value() + "\". Confira se o documento é da "
                    + companyName + ".");
        }

        List<Item> items = new ArrayList<>();
        List<RawItem> rawItems = raw.items() == null ? List.of() : raw.items();
        for (int i = 0; i < rawItems.size(); i++) {
            Item item = item(rawItems.get(i), i + 1, warnings);
            if (item != null) {
                items.add(item);
            }
        }
        checkTotal(items, totalValue.value(), warnings);

        return new ReadingResult(
                documentType(raw.documentType()),
                number,
                signedAt,
                validFrom,
                validUntil,
                allowsAdhesion,
                totalValue,
                agencyName,
                supplierName,
                List.copyOf(items),
                List.copyOf(warnings));
    }

    private static Item item(RawItem raw, int position, List<String> warnings) {
        if (raw == null || raw.description() == null || raw.description().isBlank()) {
            warnings.add("O item " + position + " veio sem descrição e foi deixado de fora.");
            return null;
        }
        String label = "item " + position;
        BigDecimal quantity = number(raw.quantity(), 3);
        if (quantity == null || quantity.signum() <= 0) {
            warnings.add("Não deu para ler a quantidade do " + label + ".");
            quantity = null;
        }
        BigDecimal unitPrice = number(raw.unitPrice(), 4);
        if (unitPrice == null || unitPrice.signum() < 0) {
            warnings.add("Não deu para ler o preço unitário do " + label + ".");
            unitPrice = null;
        }
        return new Item(
                blankToNull(raw.lot(), 40),
                blankToNull(raw.description(), 500),
                blankToNull(raw.unit(), 20),
                quantity,
                unitPrice,
                page(raw.page()));
    }

    private static void checkTotal(List<Item> items, BigDecimal total, List<String> warnings) {
        if (total == null
                || items.isEmpty()
                || items.stream().anyMatch(i -> i.quantity() == null || i.unitPrice() == null)) {
            return;
        }
        BigDecimal sum = items.stream()
                .map(i -> i.quantity().multiply(i.unitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal difference = sum.subtract(total).abs();
        if (difference.compareTo(total.multiply(TOTAL_TOLERANCE)) > 0) {
            warnings.add("A soma dos itens (" + BrFormat.money(sum) + ") não bate com o valor total lido ("
                    + BrFormat.money(total) + "). Pode faltar item ou ter preço lido errado.");
        }
    }

    static Value<String> text(RawField field, int max) {
        if (field == null) {
            return Value.empty();
        }
        String value = blankToNull(field.value(), max);
        return value == null ? Value.empty() : new Value<>(value, page(field.page()));
    }

    static Value<LocalDate> date(RawField field, String label, List<String> warnings) {
        if (field == null || field.value() == null || field.value().isBlank()) {
            return Value.empty();
        }
        String value = field.value().trim();
        for (DateTimeFormatter format : List.of(DateTimeFormatter.ISO_LOCAL_DATE, BR_DATE)) {
            try {
                return new Value<>(LocalDate.parse(value, format), page(field.page()));
            } catch (DateTimeParseException ignored) {
                // tenta o próximo formato
            }
        }
        warnings.add("Não deu para ler " + label + " (\"" + value + "\").");
        return Value.empty();
    }

    private static Value<Boolean> yesNo(RawField field) {
        if (field == null || field.value() == null) {
            return Value.empty();
        }
        String value = plain(field.value());
        Boolean answer = switch (value) {
            case "sim", "true", "s" -> Boolean.TRUE;
            case "nao", "false", "n" -> Boolean.FALSE;
            default -> null;
        };
        return answer == null ? Value.empty() : new Value<>(answer, page(field.page()));
    }

    private static Value<BigDecimal> money(RawField field, List<String> warnings) {
        if (field == null || field.value() == null || field.value().isBlank()) {
            return Value.empty();
        }
        BigDecimal value = number(field.value(), 2);
        if (value == null || value.signum() < 0) {
            warnings.add("Não deu para ler o valor total (\"" + field.value().trim() + "\").");
            return Value.empty();
        }
        return new Value<>(value, page(field.page()));
    }

    /**
     * Lê um número pedido como "1234.56", aceitando também o jeito brasileiro ("R$ 1.234,56"), que o modelo às vezes
     * copia do documento. Com vírgula, os pontos são milhar; sem vírgula, o ponto é decimal.
     */
    static BigDecimal number(String text, int scale) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.replace("R$", "").replaceAll("[\\s\\u00A0]", "");
        if (value.contains(",")) {
            value = value.replace(".", "").replace(",", ".");
        }
        if (!DIGITS.matcher(value).matches()) {
            return null;
        }
        return new BigDecimal(value).setScale(scale, RoundingMode.HALF_UP);
    }

    private static String documentType(String value) {
        String type = value == null ? "" : plain(value).toUpperCase(Locale.ROOT);
        return switch (type) {
            case "ATA", "CONTRATO" -> type;
            default -> "OUTRO";
        };
    }

    /** Compara pela primeira palavra significativa do nome ("Shema"), sem acento e sem caixa. */
    private static boolean sameCompany(String supplier, String company) {
        if (company == null || company.isBlank()) {
            return true;
        }
        String key = plain(company).split("\\s+")[0];
        return plain(supplier).contains(key);
    }

    static String plain(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    static Integer page(int page) {
        return page > 0 ? page : null;
    }

    static String blankToNull(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
