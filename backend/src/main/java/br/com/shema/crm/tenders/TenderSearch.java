package br.com.shema.crm.tenders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** API pública de busca de licitações, com os mesmos filtros da lista (Fase 11: o assistente de perguntas). */
public interface TenderSearch {

    /**
     * Tudo opcional.
     *
     * @param statuses nomes das situações (EM_DISPUTA, VENCIDA, CONTRATO_ASSINADO...); nome desconhecido é ignorado
     * @param from início do período, na data que importa para a situação (vitória, assinatura, encerramento)
     */
    record Query(
            Set<String> statuses,
            String text,
            UUID agencyId,
            String state,
            UUID ownerId,
            BigDecimal minValue,
            LocalDate from,
            LocalDate to) {}

    record Row(
            UUID id,
            String agencyName,
            String identification,
            String object,
            String status,
            String statusLabel,
            BigDecimal totalValue,
            LocalDate wonAt,
            String city,
            String state,
            UUID ownerId) {}

    /** @param total quantas licitações passam no filtro (as linhas vêm no máximo {@code limit}, as de maior valor) */
    record Result(List<Row> rows, long total) {}

    Result search(Query query, int limit);
}
