package br.com.shema.crm.pricerecords;

import br.com.shema.crm.tenders.AgencyRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * API pública da oferta de carona (Fase 10, ADR 0019): o que a Shema pode oferecer de uma ata a um município
 * candidato, com quanto ele pode pedir de cada item e o que conferir antes. Base do kit de carona escrito com IA no
 * módulo {@code extraction}.
 */
public interface CaronaOffers {

    /**
     * @param agencyUsed quanto o candidato já aderiu do item
     * @param maxQuantity quanto ele ainda pode aderir: o limite por órgão, sem passar do total das caronas
     */
    @Schema(name = "CaronaOfferItemResponse")
    record OfferItem(
            UUID itemId,
            String lot,
            String description,
            String unit,
            BigDecimal registered,
            BigDecimal unitPrice,
            BigDecimal agencyUsed,
            BigDecimal maxQuantity) {

        public BigDecimal maxValue() {
            return maxQuantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        }
    }

    /** Algo para a pessoa conferir antes de mandar o kit. Não impede gerar. */
    @Schema(name = "CaronaOfferWarningResponse")
    record OfferWarning(String code, String message) {}

    /**
     * @param manager órgão gerenciador (o da licitação da ata)
     * @param managerMunicipal gerenciador municipal: a adesão depende da lei do município que adere
     * @param maxValue ata sem itens: quanto o candidato pode aderir em valor; nulo quando a ata tem itens
     * @param canEdit quem pede pode salvar documentos na licitação da ata
     */
    @Schema(name = "CaronaOfferDetailResponse")
    record Offer(
            UUID priceRecordId,
            UUID tenderId,
            String number,
            LocalDate signedAt,
            LocalDate validUntil,
            long daysLeft,
            String tenderIdentification,
            String object,
            AgencyRef manager,
            boolean managerMunicipal,
            AgencyRef candidate,
            List<OfferItem> items,
            BigDecimal maxValue,
            int agencyCount,
            int maxAgencies,
            int maxPerAgencyPercent,
            List<OfferWarning> warnings,
            boolean canEdit) {}

    /**
     * Prepara a oferta. Recusa (regra de domínio) ata vencida, ata que não permite adesão e o próprio gerenciador como
     * candidato; o resto vira aviso.
     */
    Offer prepare(UUID priceRecordId, UUID agencyId);
}
