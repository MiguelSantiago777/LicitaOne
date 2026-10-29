package br.com.shema.crm.pricerecords.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.shema.crm.pricerecords.domain.PriceRecordDrafts.CaronaDraft;
import br.com.shema.crm.pricerecords.domain.PriceRecordDrafts.ItemDraft;
import br.com.shema.crm.pricerecords.domain.PriceRecordDrafts.LineDraft;
import br.com.shema.crm.pricerecords.domain.PriceRecordDrafts.RecordDraft;
import br.com.shema.crm.shared.domain.DomainRuleViolation;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PriceRecordTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);
    private static final Instant NOW = Instant.parse("2026-10-29T13:00:00Z");
    private static final CaronaLimits LIMITS = CaronaLimits.DEFAULT;
    private static final BigDecimal TENDER_VALUE = new BigDecimal("237000.00");

    private static void assertRule(Runnable action, String rule) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DomainRuleViolation.class)
                .extracting(e -> ((DomainRuleViolation) e).rule())
                .isEqualTo(rule);
    }

    private static PriceRecord ata(boolean allowsAdhesion) {
        return new PriceRecord(
                UUID.randomUUID(),
                new RecordDraft(
                        " ARP 017/2026 ",
                        LocalDate.of(2026, 3, 1),
                        LocalDate.of(2027, 2, 28),
                        allowsAdhesion,
                        null,
                        null));
    }

    /** Ata da SEFAZ do seed: banner 125 un a R$ 1.440 e adesivo 1.000 un a R$ 30. */
    private static PriceRecord withItems() {
        PriceRecord ata = ata(true);
        ata.replaceItems(List.of(
                new ItemDraft(
                        null, null, "Banner lona 3,00 x 2,00 m", "un", new BigDecimal("125"), new BigDecimal("1440")),
                new ItemDraft(null, null, "Adesivo perfurado", "un", new BigDecimal("1000"), new BigDecimal("30"))));
        return ata;
    }

    private static CaronaDraft carona(UUID agency, LocalDate at, List<LineDraft> lines) {
        return new CaronaDraft(agency, at, lines, null, null, null);
    }

    private static UUID banner(PriceRecord ata) {
        return ata.getItems().getFirst().getId();
    }

    @Test
    void caronaValueComesFromTheItemsAndCountsAgainstTheItemLimit() {
        PriceRecord ata = withItems();
        UUID mesquita = UUID.randomUUID();
        Carona first = ata.registerCarona(
                carona(mesquita, TODAY, List.of(new LineDraft(banner(ata), new BigDecimal("100")))),
                TENDER_VALUE,
                LIMITS,
                false,
                "Edwin",
                NOW);
        assertThat(first.getValue()).isEqualByComparingTo("144000.00");
        assertThat(first.isLimitsOverridden()).isFalse();
        assertThat(ata.usage(LIMITS).getFirst().used()).isEqualByComparingTo("100");
        assertThat(ata.usage(LIMITS).getFirst().limit()).isEqualByComparingTo("250");

        // 100 + 160 = 260 > 200% de 125: precisa confirmar
        PriceRecord.CaronaCheck check = ata.checkCarona(
                carona(UUID.randomUUID(), TODAY, List.of(new LineDraft(banner(ata), new BigDecimal("160")))),
                TENDER_VALUE,
                LIMITS);
        assertThat(check.warnings()).extracting(PriceRecord.LimitWarning::code).containsExactly("ITEM_LIMIT");
        assertThat(check.warnings().getFirst().message()).contains("260").contains("250");
        assertRule(
                () -> ata.registerCarona(
                        carona(UUID.randomUUID(), TODAY, List.of(new LineDraft(banner(ata), new BigDecimal("160")))),
                        TENDER_VALUE,
                        LIMITS,
                        false,
                        "Edwin",
                        NOW),
                "CARONA_LIMITS");
        assertThat(ata.getCaronas()).hasSize(1);

        Carona confirmed = ata.registerCarona(
                carona(UUID.randomUUID(), TODAY, List.of(new LineDraft(banner(ata), new BigDecimal("160")))),
                TENDER_VALUE,
                LIMITS,
                true,
                "Edwin",
                NOW);
        assertThat(confirmed.isLimitsOverridden()).isTrue();
        assertThat(confirmed.getOverrideNote()).startsWith("Registrada mesmo assim por Edwin");
    }

    @Test
    void fifthAgencyExpiredAtaAndSameAgencyAreFlagged() {
        PriceRecord ata = ata(true);
        for (int i = 0; i < 4; i++) {
            ata.registerCarona(
                    new CaronaDraft(UUID.randomUUID(), TODAY, List.of(), new BigDecimal("10000"), null, null),
                    TENDER_VALUE,
                    LIMITS,
                    false,
                    "Edwin",
                    NOW);
        }
        assertThat(ata.agencyCount()).isEqualTo(4);
        assertThat(ata.isOpportunity(TODAY, LIMITS)).isFalse();

        PriceRecord.CaronaCheck fifth = ata.checkCarona(
                new CaronaDraft(
                        UUID.randomUUID(), LocalDate.of(2027, 3, 1), List.of(), new BigDecimal("1000"), null, null),
                TENDER_VALUE,
                LIMITS);
        assertThat(fifth.warnings())
                .extracting(PriceRecord.LimitWarning::code)
                .containsExactlyInAnyOrder("ATA_EXPIRED", "MAX_AGENCIES");

        UUID repeated = ata.getCaronas().getFirst().getAgencyId();
        assertThat(ata.checkCarona(
                                new CaronaDraft(repeated, TODAY, List.of(), new BigDecimal("1000"), null, null),
                                TENDER_VALUE,
                                LIMITS)
                        .warnings())
                .extracting(PriceRecord.LimitWarning::code)
                .containsExactly("SAME_AGENCY");
    }

    @Test
    void withoutItemsTheLimitIsOnTheValue() {
        PriceRecord ata = ata(true);
        ata.registerCarona(
                new CaronaDraft(UUID.randomUUID(), TODAY, List.of(), new BigDecimal("400000"), null, null),
                TENDER_VALUE,
                LIMITS,
                false,
                "Edwin",
                NOW);
        // 400.000 + 100.000 > 200% de 237.000 (474.000)
        PriceRecord.CaronaCheck check = ata.checkCarona(
                new CaronaDraft(UUID.randomUUID(), TODAY, List.of(), new BigDecimal("100000"), null, null),
                TENDER_VALUE,
                LIMITS);
        assertThat(check.warnings()).extracting(PriceRecord.LimitWarning::code).containsExactly("VALUE_LIMIT");
        assertThat(check.warnings().getFirst().message())
                .contains("R$ 500.000,00")
                .contains("R$ 474.000,00");
    }

    @Test
    void firmRulesAreNotJustWarnings() {
        PriceRecord closed = ata(false);
        assertRule(
                () -> closed.checkCarona(
                        new CaronaDraft(UUID.randomUUID(), TODAY, List.of(), BigDecimal.TEN, null, null),
                        TENDER_VALUE,
                        LIMITS),
                "ADHESION_NOT_ALLOWED");

        PriceRecord ata = withItems();
        assertRule(
                () -> ata.checkCarona(
                        new CaronaDraft(UUID.randomUUID(), TODAY, List.of(), BigDecimal.TEN, null, null),
                        TENDER_VALUE,
                        LIMITS),
                "CARONA_LINES_REQUIRED");
        assertRule(
                () -> ata.checkCarona(
                        carona(UUID.randomUUID(), TODAY, List.of(new LineDraft(UUID.randomUUID(), BigDecimal.ONE))),
                        TENDER_VALUE,
                        LIMITS),
                "ITEM_NOT_FOUND");
        assertRule(
                () -> new PriceRecord(
                        UUID.randomUUID(),
                        new RecordDraft("ARP 1", LocalDate.of(2027, 1, 1), LocalDate.of(2026, 1, 1), true, null, null)),
                "INVALID_VALIDITY");
    }

    @Test
    void itemWithCaronaCanBeChangedButNotRemoved() {
        PriceRecord ata = withItems();
        UUID bannerId = banner(ata);
        ata.registerCarona(
                carona(UUID.randomUUID(), TODAY, List.of(new LineDraft(bannerId, new BigDecimal("10")))),
                TENDER_VALUE,
                LIMITS,
                false,
                "Edwin",
                NOW);

        assertRule(
                () -> ata.replaceItems(
                        List.of(new ItemDraft(null, null, "Outro", "un", BigDecimal.ONE, BigDecimal.ONE))),
                "ITEM_HAS_CARONAS");

        ata.replaceItems(List.of(new ItemDraft(
                bannerId,
                null,
                "Banner lona 440 g 3,00 x 2,00 m",
                "un",
                new BigDecimal("125"),
                new BigDecimal("1440"))));
        assertThat(ata.getItems()).singleElement().satisfies(i -> {
            assertThat(i.getId()).isEqualTo(bannerId);
            assertThat(i.getDescription()).isEqualTo("Banner lona 440 g 3,00 x 2,00 m");
        });
    }

    @Test
    void validityDecidesOpportunity() {
        PriceRecord ata = ata(true);
        assertThat(ata.getNumber()).isEqualTo("ARP 017/2026");
        assertThat(ata.isValidOn(LocalDate.of(2027, 2, 28))).isTrue();
        assertThat(ata.isValidOn(LocalDate.of(2027, 3, 1))).isFalse();
        assertThat(ata.isOpportunity(TODAY, LIMITS)).isTrue();
        assertThat(ata(false).isOpportunity(TODAY, LIMITS)).isFalse();
    }
}
