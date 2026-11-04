package br.com.shema.crm.contracts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * API pública com o que a IA precisa saber de um contrato: órgão, número, datas, valores e os itens com preço e saldo.
 * Usada pela leitura de pedido (Fase 8), para relacionar cada linha pedida com um item, e pelo follow-up (Fase 9),
 * para o texto citar o saldo e o último pedido.
 */
public interface ContractOrderContext {

    Optional<OrderContext> find(UUID contractId);

    /** Contratos ainda abertos da licitação, do mais antigo para o mais novo. */
    List<OrderContext> openForTender(UUID tenderId);

    /**
     * @param label "Prefeitura de Magé · Contrato 12/2026", para telas e para o modelo
     * @param ownerId responsável pela licitação, para conferir quem pode pedir a leitura
     * @param lastOrderAt data do último pedido, ou nulo se ainda não houve
     */
    record OrderContext(
            UUID contractId,
            UUID tenderId,
            UUID ownerId,
            String label,
            String agencyName,
            String kindLabel,
            String number,
            LocalDate signedAt,
            LocalDate validUntil,
            boolean closed,
            BigDecimal value,
            BigDecimal ordered,
            BigDecimal remaining,
            LocalDate lastOrderAt,
            List<Item> items) {

        public OrderContext {
            items = List.copyOf(items);
        }
    }

    /** Um item do contrato, na ordem do cadastro, com o saldo a pedir. */
    record Item(
            UUID id,
            String lot,
            String description,
            String unit,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal remaining) {}
}
