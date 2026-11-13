package br.com.shema.crm.extraction.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.shema.crm.extraction.domain.CaronaLetters.Kit;
import br.com.shema.crm.extraction.domain.CaronaLetters.KitLine;
import br.com.shema.crm.pricerecords.CaronaOffers.Offer;
import br.com.shema.crm.pricerecords.CaronaOffers.OfferItem;
import br.com.shema.crm.shared.company.CompanyData;
import br.com.shema.crm.tenders.AgencyRef;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CaronaLettersTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

    private static final CompanyData COMPANY = new CompanyData(
            "Shema Suprimentos e Serviços Ltda",
            "12.345.678/0001-90",
            "Rua das Flores, 10, Centro, Niterói/RJ",
            "Niterói/RJ",
            "Maria Souza",
            "Sócia-administradora",
            "comercial@shema.com.br",
            "(21) 3333-4444");

    private static final OfferItem BANNER = new OfferItem(
            UUID.randomUUID(),
            "1",
            "Banner lona 3,00 x 2,00 m",
            "un",
            new BigDecimal("125"),
            new BigDecimal("1440.00"),
            BigDecimal.ZERO,
            new BigDecimal("62.5"));

    private static Offer offer() {
        return new Offer(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ARP 017/2026",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2027, 2, 28),
                116,
                "PE 12/2026",
                "Material gráfico para campanhas",
                new AgencyRef(UUID.randomUUID(), "Secretaria de Fazenda do RJ", "Rio de Janeiro", "RJ"),
                false,
                new AgencyRef(UUID.randomUUID(), "Prefeitura de Mesquita", "Mesquita", "RJ"),
                List.of(BANNER),
                null,
                1,
                4,
                50,
                List.of(),
                true);
    }

    private static final List<KitLine> LINES = List.of(new KitLine(BANNER, new BigDecimal("60")));

    @Test
    void withoutAiTheKitComesFromTheFixedTemplates() {
        Kit kit = CaronaLetters.kit(offer(), LINES, null, COMPANY, TODAY, "Edwin Lima", null);

        assertThat(kit.writtenByAi()).isFalse();
        assertThat(kit.total()).isEqualByComparingTo("86400.00");
        assertThat(kit.warnings()).isEmpty();
        assertThat(kit.presentation().subject()).isEqualTo("Adesão à Ata de Registro de Preços nº ARP 017/2026");
        assertThat(kit.presentation().body())
                .contains("Prefeitura de Mesquita")
                .contains("Lote 1 · Banner lona 3,00 x 2,00 m: 60 un × R$ 1.440,00 = R$ 86.400,00")
                .contains("Edwin Lima")
                .contains("comercial@shema.com.br");
        assertThat(kit.request().letterhead()).isFalse();
        assertThat(kit.request().body())
                .contains("Ofício nº [número]/2026")
                .contains("Mesquita/RJ, [data].")
                .contains("cujo objeto é material gráfico para campanhas")
                .contains("art. 86, § 2º, da Lei nº 14.133/2021")
                .contains("[Descreva por que a adesão é vantajosa")
                .contains("CNPJ 12.345.678/0001-90");
        assertThat(kit.consent().letterhead()).isTrue();
        assertThat(kit.consent().body())
                .startsWith("OFÍCIO DE ANUÊNCIA")
                .contains("Niterói/RJ, 4 de novembro de 2026.")
                .contains("Com cópia ao(à) Secretaria de Fazenda do RJ, órgão gerenciador")
                .contains("sem prejuízo das obrigações assumidas com o órgão gerenciador")
                .contains("Valor total: R$ 86.400,00")
                .endsWith("Maria Souza\nSócia-administradora\nShema Suprimentos e Serviços Ltda");
    }

    @Test
    void theAiWritesThePresentationAndJustificationButNotTheConsent() {
        RawCaronaKit raw = new RawCaronaKit(
                "Banners pela ARP 017/2026",
                "Prezados(as), conforme a Lei nº 14.133, o município pode aderir...",
                "O município precisa dos banners para a campanha de vacinação.",
                List.of(" ", "Confirme o nome do secretário."));

        Kit kit = CaronaLetters.kit(offer(), LINES, null, COMPANY, TODAY, "Edwin Lima", raw);

        assertThat(kit.writtenByAi()).isTrue();
        assertThat(kit.presentation().subject()).isEqualTo("Banners pela ARP 017/2026");
        assertThat(kit.request().body()).contains("campanha de vacinação");
        assertThat(kit.consent().body()).doesNotContain("vacinação");
        assertThat(kit.warnings())
                .containsExactly(
                        "Confirme o nome do secretário.",
                        "O texto da IA cita lei ou artigo. Confira a citação ou apague: a base legal já está no"
                                + " ofício.");
    }

    @Test
    void missingCompanyDataShowsTheFieldAndWarns() {
        CompanyData blank = new CompanyData("Shema", null, " ", null, null, null, null, null);

        Kit kit = CaronaLetters.kit(offer(), LINES, null, blank, TODAY, "Edwin Lima", null);

        assertThat(kit.consent().body())
                .contains("CNPJ sob o nº [CNPJ da empresa]")
                .contains("[Nome do representante");
        assertThat(kit.warnings()).singleElement().asString().contains("CNPJ, endereço, cidade");
    }

    @Test
    void ataWithoutItemsOffersTheValueAndEmptyAiTextFails() {
        Offer noItems = new Offer(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ARP 9/2026",
                null,
                LocalDate.of(2027, 2, 28),
                116,
                "PE 9/2026",
                "SRP de toner",
                null,
                true,
                new AgencyRef(UUID.randomUUID(), "Prefeitura de Magé", null, null),
                List.of(),
                new BigDecimal("50000.00"),
                0,
                4,
                50,
                List.of(),
                false);

        Kit kit = CaronaLetters.kit(noItems, List.of(), new BigDecimal("30000"), COMPANY, TODAY, "Edwin Lima", null);
        assertThat(kit.total()).isEqualByComparingTo("30000.00");
        assertThat(kit.request().body())
                .contains("[Cidade], [data].")
                .contains("Ao(À) [órgão gerenciador]")
                .contains("cujo objeto é SRP de toner")
                .contains("- Fornecimento conforme a ata, até R$ 30.000,00");

        assertThatThrownBy(() -> CaronaLetters.kit(
                        noItems,
                        List.of(),
                        new BigDecimal("30000"),
                        COMPANY,
                        TODAY,
                        "Edwin Lima",
                        new RawCaronaKit("", " ", "", List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
