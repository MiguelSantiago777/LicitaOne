package br.com.shema.crm.pricerecords;

import br.com.shema.crm.tenders.AgencyRef;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** API pública de busca de atas e de municípios sem ata (Fase 11: o assistente de perguntas). */
public interface PriceRecordSearch {

    /**
     * @param situation VIGENTE, VENCENDO ou VENCIDA
     * @param opportunity vigente, permite adesão e ainda cabe outro órgão
     * @param usedPercent quanto as caronas já somam do valor de referência
     */
    record Row(
            UUID id,
            UUID tenderId,
            String number,
            String agencyName,
            String object,
            LocalDate validUntil,
            long daysLeft,
            String situation,
            boolean allowsAdhesion,
            boolean opportunity,
            int agencyCount,
            BigDecimal referenceValue,
            BigDecimal usedPercent) {}

    /** @param filter VIGENTES, OPORTUNIDADES, VENCENDO, VENCIDAS ou TODAS (desconhecido vale VIGENTES) */
    List<Row> search(String filter, String text);

    /** Prefeituras sem licitação vencida e sem carona: onde oferecer as atas. */
    List<AgencyRef> prospects(String state, String text);
}
