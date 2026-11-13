package br.com.shema.crm.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Valor em reais por extenso, como vai em proposta e contrato: "mil duzentos e trinta e quatro reais e cinquenta
 * centavos", "um milhão de reais". Vai até 999 bilhões.
 */
public final class AmountInWords {

    private static final String[] UNITS = {
        "",
        "um",
        "dois",
        "três",
        "quatro",
        "cinco",
        "seis",
        "sete",
        "oito",
        "nove",
        "dez",
        "onze",
        "doze",
        "treze",
        "quatorze",
        "quinze",
        "dezesseis",
        "dezessete",
        "dezoito",
        "dezenove"
    };

    private static final String[] TENS = {
        "", "", "vinte", "trinta", "quarenta", "cinquenta", "sessenta", "setenta", "oitenta", "noventa"
    };

    private static final String[] HUNDREDS = {
        "",
        "cento",
        "duzentos",
        "trezentos",
        "quatrocentos",
        "quinhentos",
        "seiscentos",
        "setecentos",
        "oitocentos",
        "novecentos"
    };

    private static final long LIMIT = 1_000_000_000_000L;

    private AmountInWords() {}

    public static String reais(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("Valor por extenso precisa de um número positivo ou zero");
        }
        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);
        long whole = rounded.longValue();
        int cents = rounded.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        if (whole >= LIMIT) {
            throw new IllegalArgumentException("Valor grande demais para escrever por extenso");
        }
        if (whole == 0 && cents == 0) {
            return "zero real";
        }
        String centsText = cents == 0 ? "" : group(cents) + (cents == 1 ? " centavo" : " centavos");
        if (whole == 0) {
            return centsText;
        }
        String reaisText = integer(whole) + currency(whole);
        return cents == 0 ? reaisText : reaisText + " e " + centsText;
    }

    /** "um real", "dois reais" e, para milhão ou bilhão redondo, "um milhão de reais". */
    private static String currency(long whole) {
        if (whole == 1) {
            return " real";
        }
        boolean roundMillions = whole >= 1_000_000 && whole % 1_000_000 == 0;
        return roundMillions ? " de reais" : " reais";
    }

    private static String integer(long n) {
        int billions = (int) (n / 1_000_000_000L);
        int millions = (int) (n / 1_000_000L % 1000);
        int thousands = (int) (n / 1000 % 1000);
        int units = (int) (n % 1000);
        List<String> parts = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        if (billions > 0) {
            parts.add(group(billions) + (billions == 1 ? " bilhão" : " bilhões"));
            values.add(billions);
        }
        if (millions > 0) {
            parts.add(group(millions) + (millions == 1 ? " milhão" : " milhões"));
            values.add(millions);
        }
        if (thousands > 0) {
            parts.add(thousands == 1 ? "mil" : group(thousands) + " mil");
            values.add(thousands);
        }
        if (units > 0) {
            parts.add(group(units));
            values.add(units);
        }
        StringBuilder text = new StringBuilder(parts.getFirst());
        for (int i = 1; i < parts.size(); i++) {
            boolean last = i == parts.size() - 1;
            int value = values.get(i);
            // "mil e cem", "dois mil e cinco", mas "mil duzentos e trinta"
            text.append(last && (value < 100 || value % 100 == 0) ? " e " : " ");
            text.append(parts.get(i));
        }
        return text.toString();
    }

    /** De 1 a 999. */
    private static String group(int n) {
        if (n == 100) {
            return "cem";
        }
        List<String> parts = new ArrayList<>();
        if (n >= 100) {
            parts.add(HUNDREDS[n / 100]);
        }
        int rest = n % 100;
        if (rest > 0 && rest < 20) {
            parts.add(UNITS[rest]);
        } else if (rest >= 20) {
            parts.add(TENS[rest / 10]);
            if (rest % 10 > 0) {
                parts.add(UNITS[rest % 10]);
            }
        }
        return String.join(" e ", parts);
    }
}
