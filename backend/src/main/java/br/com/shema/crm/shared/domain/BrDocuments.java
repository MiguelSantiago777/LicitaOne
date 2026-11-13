package br.com.shema.crm.shared.domain;

import java.util.Locale;

/**
 * CPF e CNPJ: conferência dos dígitos verificadores e formatação. O CNPJ aceita o formato alfanumérico da Receita
 * (IN RFB 2.229/2024, em vigor desde julho de 2026): 12 letras ou números e 2 dígitos verificadores numéricos. No
 * cálculo, cada caractere vale o código ASCII menos 48, o que dá o mesmo resultado do formato antigo para dígitos.
 */
public final class BrDocuments {

    private static final int[] CNPJ_WEIGHTS = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private BrDocuments() {}

    /** CPF só com os 11 dígitos, ou nulo se veio em branco. */
    public static String cpf(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("[\\s.\\-]", "");
        if (!digits.matches("\\d{11}") || digits.chars().distinct().count() == 1 || !cpfCheckDigitsMatch(digits)) {
            throw new DomainRuleViolation("CPF_INVALID", "CPF inválido. Confira os números.");
        }
        return digits;
    }

    /** CNPJ sem pontuação e em maiúsculas, ou nulo se veio em branco. */
    public static String cnpj(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String plain = value.replaceAll("[\\s./\\-]", "").toUpperCase(Locale.ROOT);
        if (!plain.matches("[0-9A-Z]{12}\\d{2}")
                || plain.chars().distinct().count() == 1
                || !cnpjCheckDigitsMatch(plain)) {
            throw new DomainRuleViolation("CNPJ_INVALID", "CNPJ inválido. Confira os números.");
        }
        return plain;
    }

    /** 000.000.000-00; devolve o valor como veio se não tiver 11 dígitos. */
    public static String formatCpf(String digits) {
        if (digits == null || digits.length() != 11) {
            return digits;
        }
        return digits.substring(0, 3) + "." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-"
                + digits.substring(9);
    }

    /** 00.000.000/0000-00; devolve o valor como veio se não tiver 14 caracteres. */
    public static String formatCnpj(String plain) {
        if (plain == null || plain.length() != 14) {
            return plain;
        }
        return plain.substring(0, 2) + "." + plain.substring(2, 5) + "." + plain.substring(5, 8) + "/"
                + plain.substring(8, 12) + "-" + plain.substring(12);
    }

    private static boolean cpfCheckDigitsMatch(String digits) {
        for (int length = 9; length <= 10; length++) {
            int sum = 0;
            for (int i = 0; i < length; i++) {
                sum += (digits.charAt(i) - '0') * (length + 1 - i);
            }
            int check = (sum * 10) % 11 % 10;
            if (check != digits.charAt(length) - '0') {
                return false;
            }
        }
        return true;
    }

    private static boolean cnpjCheckDigitsMatch(String plain) {
        for (int length = 12; length <= 13; length++) {
            int sum = 0;
            int offset = CNPJ_WEIGHTS.length - length;
            for (int i = 0; i < length; i++) {
                sum += (plain.charAt(i) - '0') * CNPJ_WEIGHTS[offset + i];
            }
            int remainder = sum % 11;
            int check = remainder < 2 ? 0 : 11 - remainder;
            if (check != plain.charAt(length) - '0') {
                return false;
            }
        }
        return true;
    }
}
