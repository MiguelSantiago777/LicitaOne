package br.com.shema.crm.contracts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * API pública com o que a leitura de pedido com IA precisa saber de um contrato (Fase 8): órgão, número, assinatura e
 * os itens com preço e saldo, para relacionar cada linha pedida com um item e avisar quando passa do saldo.
 */
public interface ContractOrderContext {

    Optional<OrderContext> find(UUID contractId);

    /**
     * @param label "Prefeitura de Magé · Contrato 12/2026", para telas e para o modelo
     * @param ownerId responsável pela licitação, para conferir quem pode pedir a leitura
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
            boolean closed,
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
