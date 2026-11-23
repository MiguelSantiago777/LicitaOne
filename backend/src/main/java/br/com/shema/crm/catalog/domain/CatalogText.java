package br.com.shema.crm.catalog.domain;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Regras de texto do cadastro: aparar, limite de tamanho, chave de busca sem acento e UF. */
public final class CatalogText {

    private static final Set<String> STATES = Set.of(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI",
            "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    private CatalogText() {}

    /** Texto aparado, ou nulo se veio em branco; mais longo que o limite é erro de regra. */
    public static String optional(String value, int max, String where) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new DomainRuleViolation("TOO_LONG", "Use no máximo " + max + " caracteres " + where + ".");
        }
        return trimmed;
    }

    /** Como {@link #optional}, mas em branco é erro. */
    public static String required(String value, int max, String where, String missingMessage) {
        String text = optional(value, max, where);
        if (text == null) {
            throw new DomainRuleViolation("REQUIRED", missingMessage);
        }
        return text;
    }

    /** UF em maiúsculas, ou nulo se veio em branco. */
    public static String state(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String uf = value.trim().toUpperCase(Locale.ROOT);
        if (!STATES.contains(uf)) {
            throw new DomainRuleViolation("STATE_INVALID", "UF inválida. Use a sigla do estado, como RJ ou SP.");
        }
        return uf;
    }

    /** Sem acento, em minúsculas e com espaços simples: a forma usada para buscar e para comparar nomes. */
    public static String key(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** As partes não vazias, em forma de busca, separadas por espaço e cortadas no limite da coluna. */
    static String searchText(int max, String... parts) {
        String joined = Stream.of(parts)
                .filter(Objects::nonNull)
                .map(CatalogText::key)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" "));
        return joined.length() > max ? joined.substring(0, max) : joined;
    }
}
