package br.com.shema.crm.pricerecords.application;

import br.com.shema.crm.pricerecords.domain.CaronaLimits;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limites de carona ({@code shema.caronas.*}). Os padrões vêm da resposta do cliente (4 órgãos, 200%); dependem de lei
 * e de edital, por isso ficam fora do código (seção 11 da especificação).
 *
 * @param expiringDays a partir de quantos dias para o vencimento a ata aparece como "vencendo"
 */
@ConfigurationProperties("shema.caronas")
public record CaronaProperties(
        @DefaultValue("4") int maxAgencies,
        @DefaultValue("200") int maxTotalPercent,
        @DefaultValue("90") int expiringDays) {

    public CaronaLimits limits() {
        return new CaronaLimits(maxAgencies, maxTotalPercent);
    }
}
