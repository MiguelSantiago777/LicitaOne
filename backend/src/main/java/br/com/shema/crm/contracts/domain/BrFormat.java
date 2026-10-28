package br.com.shema.crm.contracts.domain;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/** Números nas mensagens de regra, no formato que o usuário lê na tela. */
final class BrFormat {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private BrFormat() {}

    static String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(value).replace(' ', ' ');
    }

    static String quantity(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(PT_BR);
        format.setMaximumFractionDigits(3);
        return format.format(value);
    }
}
