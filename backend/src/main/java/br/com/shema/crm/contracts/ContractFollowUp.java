package br.com.shema.crm.contracts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** API pública para o follow-up de pedidos (alertas e painel, ADR 0015). */
public interface ContractFollowUp {

    /** @param kind rótulo do instrumento ("Contrato", "Empenho", "Dispensa") */
    record ContractWithoutOrder(
            UUID contractId, UUID tenderId, String kind, String number, LocalDate signedAt, BigDecimal value) {}

    /** Contratos abertos, assinados até {@code signedOnOrBefore}, sem nenhum pedido, do mais antigo para o mais novo. */
    List<ContractWithoutOrder> withoutOrderSignedOnOrBefore(LocalDate signedOnOrBefore);
}
