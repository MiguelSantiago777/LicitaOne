package br.com.shema.crm.tenders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.domain.Money;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);
    private final Agency agency =
            new Agency("Prefeitura Municipal de Magé", Sphere.MUNICIPAL, "Magé", "rj", null, null, null);

    private TenderDraft draft() {
        return new TenderDraft(
                agency,
                Modality.PREGAO_ELETRONICO,
                null,
                new ProcessNumber("PE 41/2026"),
                "BLL Compras",
                null,
                "Material de papelaria",
                "Eletro e papelaria",
                80,
                Money.of("174000.00"),
                UUID.randomUUID(),
                null);
    }

    private static void assertRule(Runnable action, String rule) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DomainRuleViolation.class)
                .extracting(e -> ((DomainRuleViolation) e).rule())
                .isEqualTo(rule);
    }

    @Test
    void registersInDisputeOrAlreadyWon() {
        Tender dispute = Tender.register(draft(), TenderStatus.EM_DISPUTA, null, TODAY);
        assertThat(dispute.getStatus()).isEqualTo(TenderStatus.EM_DISPUTA);
        assertThat(dispute.getWonAt()).isNull();

        Tender won = Tender.register(draft(), TenderStatus.VENCIDA, TODAY.minusDays(3), TODAY);
        assertThat(won.getStatus()).isEqualTo(TenderStatus.VENCIDA);
        assertThat(won.getWonAt()).isEqualTo(TODAY.minusDays(3));
        assertThat(won.getProcessNumber().value()).isEqualTo("041/2026");
        assertThat(won.getTotalValue()).isEqualTo(Money.of("174000"));
    }

    @Test
    void cannotRegisterInALaterStatus() {
        assertRule(
                () -> Tender.register(draft(), TenderStatus.CONTRATO_ASSINADO, null, TODAY), "INITIAL_STATUS_INVALID");
    }

    @Test
    void registeringAsWonRequiresTheDate() {
        assertRule(() -> Tender.register(draft(), TenderStatus.VENCIDA, null, TODAY), "DATE_REQUIRED");
    }

    @Test
    void walksTheWholeLifecycle() {
        Tender t = Tender.register(draft(), TenderStatus.EM_DISPUTA, null, TODAY);
        t.transitionTo(TenderStatus.VENCIDA, LocalDate.of(2026, 5, 1), TODAY);
        t.transitionTo(TenderStatus.AGUARDANDO_ASSINATURA, null, TODAY);
        t.transitionTo(TenderStatus.CONTRATO_ASSINADO, LocalDate.of(2026, 5, 16), TODAY);
        t.transitionTo(TenderStatus.EM_ANDAMENTO, LocalDate.of(2026, 6, 1), TODAY);
        t.transitionTo(TenderStatus.ENCERRADO, LocalDate.of(2026, 10, 1), TODAY);

        assertThat(t.getStatus()).isEqualTo(TenderStatus.ENCERRADO);
        assertThat(t.getSignedAt()).isEqualTo(LocalDate.of(2026, 5, 16));
        assertThat(t.getOrdersStartedAt()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(t.getClosedAt()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(t.getStatus().next()).isEmpty();
    }

    @Test
    void cannotSkipOrGoBack() {
        Tender t = Tender.register(draft(), TenderStatus.EM_DISPUTA, null, TODAY);
        assertRule(() -> t.transitionTo(TenderStatus.CONTRATO_ASSINADO, TODAY, TODAY), "TRANSITION_NOT_ALLOWED");
        t.transitionTo(TenderStatus.PERDIDA, null, TODAY);
        assertRule(() -> t.transitionTo(TenderStatus.VENCIDA, TODAY, TODAY), "TRANSITION_NOT_ALLOWED");
    }

    @Test
    void signingRequiresDateNotInFutureAndNotBeforeVictory() {
        Tender t = Tender.register(draft(), TenderStatus.VENCIDA, LocalDate.of(2026, 9, 18), TODAY);
        assertRule(() -> t.transitionTo(TenderStatus.CONTRATO_ASSINADO, null, TODAY), "DATE_REQUIRED");
        assertRule(() -> t.transitionTo(TenderStatus.CONTRATO_ASSINADO, TODAY.plusDays(1), TODAY), "DATE_IN_FUTURE");
        assertRule(() -> t.transitionTo(TenderStatus.CONTRATO_ASSINADO, LocalDate.of(2026, 9, 1), TODAY), "DATE_ORDER");
        assertThat(t.getStatus()).isEqualTo(TenderStatus.VENCIDA);
    }

    @Test
    void closingRequiresDateAfterSignature() {
        Tender t = Tender.register(draft(), TenderStatus.VENCIDA, LocalDate.of(2026, 3, 1), TODAY);
        t.transitionTo(TenderStatus.CONTRATO_ASSINADO, LocalDate.of(2026, 4, 1), TODAY);
        assertRule(() -> t.transitionTo(TenderStatus.ENCERRADO, null, TODAY), "DATE_REQUIRED");
        assertRule(() -> t.transitionTo(TenderStatus.ENCERRADO, LocalDate.of(2026, 3, 15), TODAY), "DATE_ORDER");
        t.transitionTo(TenderStatus.ENCERRADO, LocalDate.of(2026, 10, 1), TODAY);
        assertThat(t.getStatus()).isEqualTo(TenderStatus.ENCERRADO);
    }

    @Test
    void firstOrderCannotPrecedeSignature() {
        Tender t = Tender.register(draft(), TenderStatus.VENCIDA, LocalDate.of(2026, 3, 1), TODAY);
        t.transitionTo(TenderStatus.CONTRATO_ASSINADO, LocalDate.of(2026, 4, 1), TODAY);
        assertRule(() -> t.transitionTo(TenderStatus.EM_ANDAMENTO, LocalDate.of(2026, 3, 20), TODAY), "DATE_ORDER");
    }

    @Test
    void requiresUasgOrProcessNumber() {
        TenderDraft noId = new TenderDraft(
                agency, null, null, null, null, null, "Objeto", "Material", null, Money.of("10"), null, null);
        assertRule(() -> Tender.register(noId, TenderStatus.EM_DISPUTA, null, TODAY), "IDENTIFICATION_REQUIRED");

        TenderDraft onlyUasg = new TenderDraft(
                agency,
                null,
                new Uasg("160.221"),
                null,
                " ",
                null,
                "Objeto",
                "Material",
                null,
                Money.of("10"),
                null,
                " ");
        Tender t = Tender.register(onlyUasg, TenderStatus.EM_DISPUTA, null, TODAY);
        assertThat(t.getUasg().value()).isEqualTo("160221");
        assertThat(t.getModality()).isEqualTo(Modality.PREGAO_ELETRONICO);
        assertThat(t.getPortal()).isNull();
        assertThat(t.getNotes()).isNull();
    }

    @Test
    void validatesRequiredTextsAndQuantity() {
        TenderDraft noObject = new TenderDraft(
                agency, null, new Uasg("160221"), null, null, null, " ", "Material", null, Money.of("10"), null, null);
        assertRule(() -> Tender.register(noObject, TenderStatus.EM_DISPUTA, null, TODAY), "REQUIRED");

        TenderDraft negative = new TenderDraft(
                agency,
                null,
                new Uasg("160221"),
                null,
                null,
                null,
                "Objeto",
                "Material",
                -1,
                Money.of("10"),
                null,
                null);
        assertRule(() -> Tender.register(negative, TenderStatus.EM_DISPUTA, null, TODAY), "QUANTITY_NEGATIVE");
    }

    @Test
    void snapshotShowsWhatChanged() {
        Tender t = Tender.register(draft(), TenderStatus.EM_DISPUTA, null, TODAY);
        var before = t.snapshot();
        TenderDraft changed = new TenderDraft(
                agency,
                Modality.PREGAO_ELETRONICO,
                null,
                new ProcessNumber("041/2026"),
                "BLL Compras",
                null,
                "Material de papelaria",
                "Eletro e papelaria",
                90,
                Money.of("180000"),
                before.get("Responsável") == null ? null : UUID.fromString(before.get("Responsável")),
                "Nova observação");
        t.update(changed);
        var after = t.snapshot();
        assertThat(after.get("Quantidade de itens")).isEqualTo("90");
        assertThat(after.get("Valor")).isEqualTo("180000.00");
        assertThat(after.get("Observações")).isEqualTo("Nova observação");
        assertThat(after.get("Objeto")).isEqualTo(before.get("Objeto"));
    }

    @Test
    void statusMetadata() {
        assertThat(TenderStatus.CONTRATO_ASSINADO.requiredDate()).isEqualTo(TenderStatus.DateKind.SIGNED);
        assertThat(TenderStatus.CONTRATO_ASSINADO.requiredDate().label()).isEqualTo("data de assinatura");
        assertThat(TenderStatus.EM_DISPUTA.isInitial()).isTrue();
        assertThat(TenderStatus.ENCERRADO.isInitial()).isFalse();
        assertThat(TenderStatus.VENCIDA.label()).isEqualTo("Vencida");
    }
}
