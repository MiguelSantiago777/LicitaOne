package br.com.shema.crm.contracts.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Saldo do contrato por valor e por item (ADR 0005): sempre calculado a partir dos pedidos, nunca digitado.
 *
 * @param value valor do contrato
 * @param ordered soma dos pedidos
 * @param remaining o que falta pedir (valor − pedido)
 */
public record ContractBalance(BigDecimal value, BigDecimal ordered, BigDecimal remaining, List<ItemBalance> items) {

    public record ItemBalance(
            UUID itemId,
            BigDecimal contracted,
            BigDecimal ordered,
            BigDecimal remaining,
            BigDecimal orderedValue,
            BigDecimal remainingValue) {}

    /** Percentual já pedido, de 0 a 100 (inteiro, arredondado para baixo: 99,9% ainda não é 100%). */
    public int percentOrdered() {
        if (value.signum() == 0) {
            return ordered.signum() == 0 ? 0 : 100;
        }
        return ordered.multiply(BigDecimal.valueOf(100))
                .divide(value, 0, RoundingMode.DOWN)
                .min(BigDecimal.valueOf(100))
                .intValue();
    }
}
