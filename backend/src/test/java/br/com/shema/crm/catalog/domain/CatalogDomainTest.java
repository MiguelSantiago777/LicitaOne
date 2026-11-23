package br.com.shema.crm.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogDomainTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);
    private static final UUID NICHE = UUID.randomUUID();
    private static final UUID ALFA = UUID.randomUUID();
    private static final UUID BETA = UUID.randomUUID();

    private static ProductDraft.Offer offer(UUID supplier, String price, LocalDate date) {
        return new ProductDraft.Offer(supplier, price == null ? null : new BigDecimal(price), date, 5, null);
    }

    private static ProductDraft product(List<ProductDraft.Offer> offers) {
        return new ProductDraft("Detergente neutro", null, NICHE, "un", "Limpol", "463.270", offers);
    }

    @Test
    void nicheNameKeyIgnoresAccentsCaseAndSpaces() {
        Niche niche = new Niche("  Material de   Escritório ", null);
        assertThat(niche.getName()).isEqualTo("Material de   Escritório");
        assertThat(niche.getNameKey()).isEqualTo("material de escritorio");
        assertThatThrownBy(() -> new Niche(" ", null)).isInstanceOf(DomainRuleViolation.class);
    }

    @Test
    void supplierChecksCnpjAndStateAndDropsBlankContacts() {
        SupplierDraft draft = new SupplierDraft(
                "Alfa Distribuidora Ltda",
                "Alfa",
                "12.345.678/0001-95",
                "Duque de Caxias",
                "rj",
                null,
                null,
                List.of(NICHE, NICHE),
                List.of(
                        new SupplierDraft.Contact("Carla", "(21) 98888-1001", null),
                        new SupplierDraft.Contact(" ", null, "")));
        Supplier supplier = new Supplier(draft);
        assertThat(supplier.getCnpj()).isEqualTo("12345678000195");
        assertThat(supplier.getState()).isEqualTo("RJ");
        assertThat(supplier.getNicheIds()).containsExactly(NICHE);
        assertThat(supplier.getContacts()).hasSize(1);
        assertThat(supplier.displayName()).isEqualTo("Alfa");

        assertThatThrownBy(() -> new Supplier(new SupplierDraft(
                        "X", null, "12.345.678/0001-00", null, null, null, null, List.of(), List.of())))
                .hasMessageContaining("CNPJ");
        assertThatThrownBy(() ->
                        new Supplier(new SupplierDraft("X", null, null, null, "XX", null, null, List.of(), List.of())))
                .hasMessageContaining("UF");
        assertThatThrownBy(() ->
                        new Supplier(new SupplierDraft(null, null, null, null, null, null, null, List.of(), List.of())))
                .hasMessageContaining("razão social");
    }

    @Test
    void newPriceWithoutDateIsQuotedToday() {
        Product p = new Product(product(List.of(offer(ALFA, "2.35", null))), TODAY);
        assertThat(p.getCatalogCode()).isEqualTo("463270");
        assertThat(p.getOffers().getFirst().getQuotedOn()).isEqualTo(TODAY);
    }

    @Test
    void changingThePriceRefreshesTheDateUnlessThePersonChangedIt() {
        LocalDate old = TODAY.minusDays(100);
        Product p = new Product(product(List.of(offer(ALFA, "2.35", old))), TODAY);
        UUID offerId = p.getOffers().getFirst().getId();
        assertThat(p.getOffers().getFirst().isStale(TODAY)).isTrue();

        // Mesmo preço, mesma data: nada muda
        p.update(product(List.of(offer(ALFA, "2.3500", old))), TODAY);
        assertThat(p.getOffers().getFirst().getQuotedOn()).isEqualTo(old);

        // Preço novo com a data antiga: a cotação é de hoje, e a oferta é a mesma
        p.update(product(List.of(offer(ALFA, "2.20", old))), TODAY);
        assertThat(p.getOffers().getFirst().getQuotedOn()).isEqualTo(TODAY);
        assertThat(p.getOffers().getFirst().getId()).isEqualTo(offerId);

        // Preço novo com data escolhida pela pessoa: vale a data dela
        LocalDate chosen = TODAY.minusDays(7);
        p.update(product(List.of(offer(ALFA, "2.10", chosen))), TODAY);
        assertThat(p.getOffers().getFirst().getQuotedOn()).isEqualTo(chosen);

        assertThatThrownBy(() -> p.update(product(List.of(offer(ALFA, "2.10", TODAY.plusDays(1)))), TODAY))
                .hasMessageContaining("depois de hoje");
    }

    @Test
    void bestOfferIsTheCheapestAcceptedSupplier() {
        Product p = new Product(
                product(List.of(
                        offer(ALFA, "2.35", TODAY), offer(BETA, "2.10", TODAY), offer(UUID.randomUUID(), null, null))),
                TODAY);
        assertThat(p.bestOffer(id -> true).orElseThrow().getSupplierId()).isEqualTo(BETA);
        assertThat(p.bestOffer(id -> !id.equals(BETA)).orElseThrow().getSupplierId())
                .isEqualTo(ALFA);
        assertThat(p.bestOffer(id -> false)).isEmpty();
    }

    @Test
    void oneOfferPerSupplier() {
        assertThatThrownBy(() -> new Product(product(List.of(offer(ALFA, "1", TODAY), offer(ALFA, "2", TODAY))), TODAY))
                .hasMessageContaining("duas vezes");
        assertThatThrownBy(() -> new Product(product(List.of(offer(null, "1", TODAY))), TODAY))
                .hasMessageContaining("fornecedor");
    }

    @Test
    void removedOffersDisappearAndOrderFollowsTheDraft() {
        Product p = new Product(product(List.of(offer(ALFA, "1", TODAY), offer(BETA, "2", TODAY))), TODAY);
        p.update(product(List.of(offer(BETA, "2", TODAY))), TODAY);
        assertThat(p.getOffers()).extracting(ProductOffer::getSupplierId).containsExactly(BETA);
        assertThat(p.supplierIds()).containsExactly(BETA);
    }

    @Test
    void catalogCodeIsDigitsOnly() {
        assertThatThrownBy(() -> new Product(new ProductDraft("X", null, NICHE, null, null, "ABC", List.of()), TODAY))
                .hasMessageContaining("CATMAT");
        assertThatThrownBy(() -> new Product(new ProductDraft("X", null, null, null, null, null, List.of()), TODAY))
                .hasMessageContaining("nicho");
    }

    @Test
    void fileInspectionChecksContentAndKind() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        byte[] pdf = "%PDF-1.7 teste".getBytes(StandardCharsets.US_ASCII);
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);

        assertThat(CatalogFileInspection.inspect(ProductFileKind.FOTO, "C:\\fotos\\detergente.png", png))
                .isEqualTo(new CatalogFileInspection.Accepted("detergente.png", "image/png"));
        assertThat(CatalogFileInspection.inspect(ProductFileKind.FOTO, "a.webp", webp)
                        .contentType())
                .isEqualTo("image/webp");
        assertThat(CatalogFileInspection.inspect(ProductFileKind.FICHA, "ficha.pdf", pdf)
                        .contentType())
                .isEqualTo("application/pdf");

        assertThatThrownBy(() -> CatalogFileInspection.inspect(ProductFileKind.FOTO, "ficha.pdf", pdf))
                .hasMessageContaining("JPG, PNG ou WEBP");
        assertThatThrownBy(() -> CatalogFileInspection.inspect(ProductFileKind.FICHA, "falso.pdf", png))
                .hasMessageContaining("não é de um .pdf");
        assertThatThrownBy(() -> CatalogFileInspection.inspect(ProductFileKind.FICHA, "a.exe", pdf))
                .isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> CatalogFileInspection.inspect(ProductFileKind.FICHA, "a.pdf", new byte[0]))
                .hasMessageContaining("vazio");
    }
}
