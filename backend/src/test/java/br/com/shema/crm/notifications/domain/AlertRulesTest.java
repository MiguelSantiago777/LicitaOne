package br.com.shema.crm.notifications.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.shema.crm.notifications.domain.AlertRules.ContractFacts;
import br.com.shema.crm.notifications.domain.AlertRules.RecordFacts;
import br.com.shema.crm.notifications.domain.AlertRules.TenderFacts;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AlertRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

    private static final AlertSettings SETTINGS = new AlertSettings(30, List.of(30, 90, 60), 15);

    private final AlertRules rules = new AlertRules(SETTINGS, "http://licitaone.local/");

    private static TenderFacts tender(String ownerName) {
        return new TenderFacts(
                UUID.fromString("20000000-0000-4000-8000-000000000013"),
                "Secretaria de Estado de Fazenda",
                "UASG 926001 · PE 039/2026",
                "Material gráfico",
                new BigDecimal("237000.00"),
                TODAY.minusDays(60),
                ownerName == null ? null : UUID.randomUUID(),
                ownerName);
    }

    private static ContractFacts contract(LocalDate signedAt) {
        return new ContractFacts(UUID.randomUUID(), "Contrato", "12/2026", signedAt, new BigDecimal("237000.00"));
    }

    @Test
    void contractWithoutOrderWaitsForTheConfiguredDays() {
        assertThat(rules.contractWithoutOrder(contract(TODAY.minusDays(29)), tender(null), List.of(), TODAY))
                .isEmpty();
        assertThat(rules.contractWithoutOrder(contract(TODAY.minusDays(30)), tender(null), List.of(), TODAY))
                .isPresent();
    }

    @Test
    void contractWithoutOrderWritesTheTaskTheDraftAndTheNotice() {
        ContractFacts c = contract(TODAY.minusDays(34));
        Alert alert = rules.contractWithoutOrder(
                        c, tender("Ana Souza"), List.of("compras@sefaz.rj.gov.br", "pregao@sefaz.rj.gov.br"), TODAY)
                .orElseThrow();

        assertThat(alert.key()).isEqualTo("CONTRATO_SEM_PEDIDO:" + c.id());
        assertThat(alert.rule()).isEqualTo(AlertRule.CONTRATO_SEM_PEDIDO);
        assertThat(alert.subjectId()).isEqualTo(c.id());
        assertThat(alert.title()).isEqualTo("Sem pedido há 34 dias: Secretaria de Estado de Fazenda");
        assertThat(alert.dueOn()).isEqualTo(TODAY);
        assertThat(alert.notes()).contains("Contrato nº 12/2026 assinado em 25/09/2026", "R$ 237.000,00");

        assertThat(alert.draft().to()).isEqualTo("compras@sefaz.rj.gov.br, pregao@sefaz.rj.gov.br");
        assertThat(alert.draft().subject())
                .isEqualTo("Previsão de pedidos: Contrato nº 12/2026 · UASG 926001 · PE 039/2026");
        assertThat(alert.draft().body())
                .contains("Visto que participamos do certame", "há previsão de pedidos de materiais")
                .contains("contrato nº 12/2026 em 25/09/2026")
                .endsWith("Ana Souza\nShema Suprimentos");

        assertThat(alert.notice().subject())
                .isEqualTo("[LicitaOne] Contrato sem pedido há 34 dias: Secretaria de Estado de Fazenda");
        assertThat(alert.notice().body())
                .contains("Saldo a pedir: R$ 237.000,00")
                .contains("http://licitaone.local/licitacoes/20000000-0000-4000-8000-000000000013");
    }

    @Test
    void draftWithoutContactKeepsTheRecipientEmptyAndSignsAsTheCompany() {
        Alert alert = rules.contractWithoutOrder(contract(TODAY.minusDays(40)), tender(null), List.of(), TODAY)
                .orElseThrow();
        assertThat(alert.draft().to()).isEmpty();
        assertThat(alert.draft().body()).endsWith("Atenciosamente,\nShema Suprimentos");
    }

    @Test
    void expiringRecordRaisesOnlyTheNarrowestBandReached() {
        RecordFacts record = new RecordFacts(UUID.randomUUID(), "ARP 017/2026", TODAY.plusDays(20), true);
        Alert alert = rules.priceRecordExpiring(record, tender(null), TODAY).orElseThrow();
        assertThat(alert.key()).isEqualTo("ATA_VENCENDO:" + record.id() + ":2026-11-18:30");
        assertThat(alert.title()).isEqualTo("Ata ARP 017/2026 vence em 20 dias: Secretaria de Estado de Fazenda");
        assertThat(alert.notes()).contains("permite adesão", "Confira no edital");
        assertThat(alert.draft()).isNull();
    }

    @Test
    void eachBandHasItsOwnKeyAndAnExtendedRecordStartsOver() {
        UUID id = UUID.randomUUID();
        LocalDate validUntil = TODAY.plusDays(75);
        assertThat(rules.priceRecordExpiring(new RecordFacts(id, "ARP 1", validUntil, false), tender(null), TODAY)
                        .orElseThrow()
                        .key())
                .endsWith(":90");
        assertThat(rules.priceRecordExpiring(
                                new RecordFacts(id, "ARP 1", validUntil, false), tender(null), TODAY.plusDays(20))
                        .orElseThrow()
                        .key())
                .endsWith(":60");
        // Prorrogada: a vigência nova muda a chave e as faixas recomeçam
        assertThat(rules.priceRecordExpiring(
                                new RecordFacts(id, "ARP 1", validUntil.plusYears(1), false),
                                tender(null),
                                validUntil.plusYears(1).minusDays(80))
                        .orElseThrow()
                        .key())
                .isEqualTo("ATA_VENCENDO:" + id + ":" + validUntil.plusYears(1) + ":90");
    }

    @Test
    void expiringRecordOutsideTheBandsOrAlreadyExpiredRaisesNothing() {
        assertThat(rules.priceRecordExpiring(
                        new RecordFacts(UUID.randomUUID(), "ARP 2", TODAY.plusDays(91), true), tender(null), TODAY))
                .isEmpty();
        assertThat(rules.priceRecordExpiring(
                        new RecordFacts(UUID.randomUUID(), "ARP 3", TODAY.minusDays(1), true), tender(null), TODAY))
                .isEmpty();
        assertThat(rules.priceRecordExpiring(
                                new RecordFacts(UUID.randomUUID(), "ARP 4", TODAY, false), tender(null), TODAY)
                        .orElseThrow()
                        .title())
                .startsWith("Ata ARP 4 vence hoje");
    }

    @Test
    void tenderWithoutContractIsBornOverdue() {
        TenderFacts t = tender(null);
        Alert alert = rules.tenderWithoutContract(t, TODAY).orElseThrow();
        assertThat(alert.key()).isEqualTo("LICITACAO_SEM_CONTRATO:" + t.id());
        assertThat(alert.title()).isEqualTo("Vencida há 60 dias sem contrato: Secretaria de Estado de Fazenda");
        // Venceu no dia em que o prazo passou: a tarefa já aparece atrasada
        assertThat(alert.dueOn()).isEqualTo(t.wonAt().plusDays(15));
        assertThat(alert.notice().body()).contains("Valor a contratar: R$ 237.000,00");

        assertThat(rules.tenderWithoutContract(t, t.wonAt().plusDays(14))).isEmpty();
    }

    @Test
    void settingsNeedPositiveDaysAndSortTheBands() {
        assertThat(SETTINGS.priceRecordExpiringDays()).containsExactly(90, 60, 30);
        assertThat(SETTINGS.widestExpiringBand()).isEqualTo(90);
        assertThatThrownBy(() -> new AlertSettings(0, List.of(30), 15)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AlertSettings(30, List.of(), 15)).isInstanceOf(IllegalArgumentException.class);
    }
}
