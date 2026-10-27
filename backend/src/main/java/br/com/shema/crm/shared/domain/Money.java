package br.com.shema.crm.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Valor em reais, sempre com 2 casas e nunca negativo. Dinheiro nunca é double. */
public record Money(BigDecimal amount) implements Comparable<Money> {

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) {
            throw new DomainRuleViolation("MONEY_NEGATIVE", "Valor não pode ser negativo.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new DomainRuleViolation("MONEY_SCALE", "Valor deve ter no máximo 2 casas decimais.");
        }
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }
}
