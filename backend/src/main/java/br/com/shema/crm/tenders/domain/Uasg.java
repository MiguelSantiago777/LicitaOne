package br.com.shema.crm.tenders.domain;

import br.com.shema.crm.shared.domain.DomainRuleViolation;

/** Código UASG: 6 dígitos. Aceita o valor com espaços ou pontos e guarda só os dígitos. */
public record Uasg(String value) {

    public Uasg {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        if (!digits.matches("\\d{6}")) {
            throw new DomainRuleViolation("UASG_INVALID", "A UASG tem 6 dígitos.");
        }
        value = digits;
    }

    public static Uasg ofNullable(String value) {
        return value == null || value.isBlank() ? null : new Uasg(value);
    }
}
