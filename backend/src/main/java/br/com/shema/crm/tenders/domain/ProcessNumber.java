package br.com.shema.crm.tenders.domain;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Número do processo no formato NNN/AAAA (ex.: pregão 039/2026). Aceita prefixos como "PE" ou "Pregão nº" e
 * completa o número com zeros à esquerda até 3 dígitos.
 */
public record ProcessNumber(String value) {

    private static final Pattern FORMAT = Pattern.compile("(\\d{1,5})\\s*/\\s*(\\d{4})");

    public ProcessNumber {
        Matcher m = value == null ? null : FORMAT.matcher(value);
        if (m == null || !m.find()) {
            throw new DomainRuleViolation("PROCESS_NUMBER_INVALID", "Use o formato número/ano, por exemplo 039/2026.");
        }
        int year = Integer.parseInt(m.group(2));
        if (year < 2000 || year > 2100) {
            throw new DomainRuleViolation("PROCESS_NUMBER_INVALID", "O ano do processo parece errado.");
        }
        value = String.format("%03d/%s", Integer.parseInt(m.group(1)), m.group(2));
    }

    public static ProcessNumber ofNullable(String value) {
        return value == null || value.isBlank() ? null : new ProcessNumber(value);
    }
}
