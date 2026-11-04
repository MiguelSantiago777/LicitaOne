package br.com.shema.crm.extraction.application;

import br.com.shema.crm.extraction.domain.CompanyData;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Dados da empresa para o cabeçalho das cartas e o ofício de anuência do kit de carona ({@code shema.company.*}, Fase
 * 10). Ficam no ambiente, não no código: o {@code .env.example} traz exemplos para trocar no deploy.
 */
@ConfigurationProperties("shema.company")
public record CompanyProperties(
        @DefaultValue("Shema Suprimentos e Serviços") String legalName,
        String cnpj,
        String address,
        String city,
        String representative,
        String representativeRole,
        String email,
        String phone) {

    public CompanyData data() {
        return new CompanyData(legalName, cnpj, address, city, representative, representativeRole, email, phone);
    }
}
