package br.com.shema.crm.pricerecords.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Entradas dos casos de uso da ata. */
public final class PriceRecordDrafts {

    private PriceRecordDrafts() {}

    public record RecordDraft(
            String number,
            LocalDate signedAt,
            LocalDate validUntil,
            boolean allowsAdhesion,
            String notes,
            UUID documentId) {}

    /** @param id nulo para item novo; o de um item existente para alterar sem perder as caronas ligadas a ele */
    public record ItemDraft(
            UUID id, String lot, String description, String unit, BigDecimal quantity, BigDecimal unitPrice) {}

    /** @param value usado só quando a carona não tem linhas (ata sem itens); com linhas, o valor é calculado */
    public record CaronaDraft(
            UUID agencyId, LocalDate adheredAt, List<LineDraft> lines, BigDecimal value, UUID dealId, String notes) {}

    public record LineDraft(UUID itemId, BigDecimal quantity) {}
}
