package br.com.shema.crm.contracts.domain;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Regras numéricas de itens e pedidos: quantidade com até 3 casas, valor unitário com até 4, total com 2. */
final class Quantities {

    private Quantities() {}

    static BigDecimal quantity(BigDecimal value, String what) {
        if (value == null || value.signum() <= 0) {
            throw new DomainRuleViolation(
                    "QUANTITY_NOT_POSITIVE", "A quantidade " + what + " deve ser maior que zero.");
        }
        if (value.stripTrailingZeros().scale() > 3) {
            throw new DomainRuleViolation("QUANTITY_SCALE", "A quantidade deve ter no máximo 3 casas decimais.");
        }
        return value.setScale(3, RoundingMode.UNNECESSARY);
    }

    static BigDecimal unitPrice(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new DomainRuleViolation("UNIT_PRICE_INVALID", "Informe o valor unitário (zero ou mais).");
        }
        if (value.stripTrailingZeros().scale() > 4) {
            throw new DomainRuleViolation("UNIT_PRICE_SCALE", "O valor unitário deve ter no máximo 4 casas decimais.");
        }
        return value.setScale(4, RoundingMode.UNNECESSARY);
    }

    /** Valor total de uma linha, arredondado para centavos (meio para cima, como nas planilhas). */
    static BigDecimal total(BigDecimal quantity, BigDecimal unitPrice) {
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }
}
