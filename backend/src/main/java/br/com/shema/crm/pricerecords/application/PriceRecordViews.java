package br.com.shema.crm.pricerecords.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Respostas da API de atas. */
public final class PriceRecordViews {

    private PriceRecordViews() {}

    /** VIGENTE, VENCENDO (dentro do prazo de aviso) ou VENCIDA. */
    public enum Situation {
        VIGENTE,
        VENCENDO,
        VENCIDA
    }

    @Schema(name = "PriceRecordTenderResponse")
    public record TenderLink(
            UUID id,
            String agencyName,
            String identification,
            String object,
            String materialType,
            String status,
            BigDecimal totalValue) {}

    @Schema(name = "PriceRecordLimitsResponse")
    public record Limits(int maxAgencies, int maxTotalPercent) {}

    /**
     * Linha da lista de atas.
     *
     * @param referenceValue soma dos itens da ata, ou o valor da licitação se a ata não tem itens
     * @param usedPercent quanto as caronas já somam do valor de referência
     */
    @Schema(name = "PriceRecordSummaryResponse")
    public record Summary(
            UUID id,
            TenderLink tender,
            String number,
            LocalDate validUntil,
            long daysLeft,
            Situation situation,
            boolean allowsAdhesion,
            boolean opportunity,
            int agencyCount,
            BigDecimal referenceValue,
            BigDecimal caronasValue,
            BigDecimal usedPercent) {}

    @Schema(name = "PriceRecordResponse")
    public record Detail(
            UUID id,
            TenderLink tender,
            String number,
            LocalDate signedAt,
            LocalDate validUntil,
            long daysLeft,
            Situation situation,
            boolean allowsAdhesion,
            boolean opportunity,
            String notes,
            DocumentLink document,
            Limits limits,
            int agencyCount,
            BigDecimal referenceValue,
            BigDecimal caronasValue,
            BigDecimal usedPercent,
            List<Item> items,
            List<CaronaView> caronas,
            long version,
            boolean canEdit) {}

    @Schema(name = "PriceRecordDocumentResponse")
    public record DocumentLink(UUID id, String title, int currentVersion) {}

    /**
     * @param used quanto as caronas já somam no item
     * @param limit quanto podem somar (quantitativo × limite percentual)
     */
    @Schema(name = "PriceRecordItemResponse")
    public record Item(
            UUID id,
            String lot,
            String description,
            String unit,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal total,
            BigDecimal used,
            BigDecimal limit) {}

    @Schema(name = "CaronaResponse")
    public record CaronaView(
            UUID id,
            AgencyLink agency,
            LocalDate adheredAt,
            BigDecimal value,
            DealLink deal,
            List<CaronaLine> lines,
            String notes,
            boolean limitsOverridden,
            String overrideNote,
            String createdBy,
            Instant createdAt) {}

    @Schema(name = "CaronaAgencyResponse")
    public record AgencyLink(UUID id, String name, String city, String state) {}

    @Schema(name = "CaronaDealResponse")
    public record DealLink(UUID id, String title, String status) {}

    @Schema(name = "CaronaLineResponse")
    public record CaronaLine(UUID itemId, String description, BigDecimal quantity) {}

    @Schema(name = "CaronaWarningResponse")
    public record Warning(String code, String message) {}

    /** O que a carona registraria. Com avisos, registrar exige confirmação. */
    @Schema(name = "CaronaCheckResponse")
    public record CaronaCheck(BigDecimal value, List<Warning> warnings) {}

    /** Ata de uma licitação, para os destaques nas listas e no detalhe. */
    @Schema(name = "PriceRecordHighlightResponse")
    public record Highlight(
            UUID tenderId, UUID priceRecordId, LocalDate validUntil, Situation situation, boolean opportunity) {}

    @Schema(name = "ProspectResponse")
    public record Prospect(UUID id, String name, String city, String state) {}

    @Schema(name = "PriceRecordEventResponse")
    public record Event(UUID id, String type, String summary, String userName, Instant occurredAt) {}
}
