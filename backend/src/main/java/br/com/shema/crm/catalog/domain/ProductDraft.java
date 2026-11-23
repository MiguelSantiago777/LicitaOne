package br.com.shema.crm.catalog.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** O conteúdo editável do produto, com as ofertas dos fornecedores. Os arquivos têm rota própria. */
public record ProductDraft(
        String name,
        String description,
        UUID nicheId,
        String unit,
        String brand,
        String catalogCode,
        List<Offer> offers) {

    /** Preço de custo de um fornecedor. Só o fornecedor é obrigatório: o preço pode vir depois. */
    public record Offer(
            UUID supplierId, BigDecimal unitPrice, LocalDate quotedOn, Integer leadTimeDays, BigDecimal minimumOrder) {}
}
