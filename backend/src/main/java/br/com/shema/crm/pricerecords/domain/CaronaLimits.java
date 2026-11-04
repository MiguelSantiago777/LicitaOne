package br.com.shema.crm.pricerecords.domain;

/**
 * Limites de carona respondidos pelo cliente: "em até 4 municípios, mas só pode replicar 200%". Dependem de lei e do
 * edital, então são configuráveis ({@code shema.caronas.*}) e passar deles é só sinalizado (ADR 0014).
 *
 * @param maxAgencies quantos órgãos diferentes podem aderir à mesma ata
 * @param maxTotalPercent quanto a soma das caronas pode chegar do quantitativo registrado de cada item (ou do valor da
 *     ata, se ela não tiver itens)
 * @param maxPerAgencyPercent quanto um órgão sozinho pode aderir de cada item (art. 86, § 4º da Lei 14.133: 50%);
 *     4 órgãos × 50% fecham os 200% do cliente (Fase 10, ADR 0019)
 */
public record CaronaLimits(int maxAgencies, int maxTotalPercent, int maxPerAgencyPercent) {

    public static final CaronaLimits DEFAULT = new CaronaLimits(4, 200, 50);

    public CaronaLimits {
        if (maxAgencies < 1 || maxTotalPercent < 1 || maxPerAgencyPercent < 1) {
            throw new IllegalArgumentException("Limites de carona devem ser positivos");
        }
    }
}
