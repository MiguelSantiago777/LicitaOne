package br.com.shema.crm.contracts.domain;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/** Números nas mensagens de regra, no formato que o usuário lê na tela. */
public final class BrFormat {

    /** Espaço não separável que o NumberFormat põe depois de "R$". */
    private static final char NBSP = (char) 0x00A0;

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private BrFormat() {}

    public static String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(value).replace(NBSP, ' ');
    }

    public static String quantity(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(PT_BR);
        format.setMaximumFractionDigits(3);
        return format.format(value);
    }
}
