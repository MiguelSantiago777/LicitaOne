package br.com.shema.crm.deals.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PipelineTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

    private static void assertRule(Runnable action, String rule) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DomainRuleViolation.class)
                .extracting(e -> ((DomainRuleViolation) e).rule())
                .isEqualTo(rule);
    }

    private static DealDraft draft(String title) {
        return new DealDraft(title, UUID.randomUUID(), null, new BigDecimal("118500.00"), 2, null, null);
    }

    @Test
    void stagesAreConfigurable() {
        Pipeline pipeline = new Pipeline("Funil de vendas");
        DealStage semContato = pipeline.addStage("Sem contato", StageKind.ABERTA);
        DealStage proposta = pipeline.addStage("Proposta enviada", StageKind.ABERTA);
        DealStage ganho = pipeline.addStage("Fechado ganho", StageKind.GANHA);
        pipeline.addStage("Fechado perdido", StageKind.PERDIDA);

        pipeline.renameStage(proposta.getId(), "  Proposta   enviada ao órgão ");
        pipeline.moveStage(proposta.getId(), 0);

        assertThat(pipeline.getStages())
                .extracting(DealStage::getName)
                .containsExactly("Proposta enviada ao órgão", "Sem contato", "Fechado ganho", "Fechado perdido");
        assertThat(pipeline.getStages()).extracting(DealStage::getPosition).containsExactly(0, 1, 2, 3);
        assertThat(pipeline.firstOpenStage()).isSameAs(proposta);

        pipeline.removeStage(semContato.getId(), 0);
        assertThat(pipeline.getStages()).extracting(DealStage::getPosition).containsExactly(0, 1, 2);
        assertThat(ganho.getPosition()).isEqualTo(1);
    }

    @Test
    void stageWithDealsOrLastOfItsKindStays() {
        Pipeline pipeline = new Pipeline("Funil de vendas");
        DealStage aberta = pipeline.addStage("Sem contato", StageKind.ABERTA);
        DealStage ganho = pipeline.addStage("Fechado ganho", StageKind.GANHA);
        pipeline.addStage("Fechado perdido", StageKind.PERDIDA);
        DealStage outra = pipeline.addStage("Contato feito", StageKind.ABERTA);

        assertRule(() -> pipeline.removeStage(outra.getId(), 3), "STAGE_NOT_EMPTY");
        assertRule(() -> pipeline.removeStage(ganho.getId(), 0), "LAST_STAGE_OF_KIND");
        pipeline.removeStage(outra.getId(), 0);
        assertRule(() -> pipeline.removeStage(aberta.getId(), 0), "LAST_STAGE_OF_KIND");
        assertRule(() -> pipeline.stage(UUID.randomUUID()), "STAGE_NOT_FOUND");
    }

    @Test
    void statusFollowsTheStageKind() {
        Pipeline pipeline = new Pipeline("Funil de vendas");
        DealStage aberta = pipeline.addStage("Proposta enviada", StageKind.ABERTA);
        DealStage ganho = pipeline.addStage("Fechado ganho", StageKind.GANHA);
        DealStage perdido = pipeline.addStage("Fechado perdido", StageKind.PERDIDA);

        Deal deal = new Deal(pipeline.getId(), aberta, 0, draft("Carona SEFAZ"), TODAY);
        assertThat(deal.getStatus()).isEqualTo(DealStatus.EM_ANDAMENTO);
        assertThat(deal.getClosedAt()).isNull();

        deal.placeAt(ganho, 0, TODAY);
        assertThat(deal.getStatus()).isEqualTo(DealStatus.GANHA);
        assertThat(deal.getClosedAt()).isEqualTo(TODAY);

        // de ganha para perdida continua fechada desde o mesmo dia
        deal.placeAt(perdido, 0, TODAY.plusDays(3));
        assertThat(deal.getStatus()).isEqualTo(DealStatus.PERDIDA);
        assertThat(deal.getClosedAt()).isEqualTo(TODAY);

        deal.placeAt(aberta, 0, TODAY.plusDays(5));
        assertThat(deal.getStatus()).isEqualTo(DealStatus.EM_ANDAMENTO);
        assertThat(deal.getClosedAt()).isNull();
    }

    @Test
    void dealNeedsAgencyOrTenderAndValidPriority() {
        Pipeline pipeline = new Pipeline("Funil de vendas");
        DealStage aberta = pipeline.addStage("Sem contato", StageKind.ABERTA);
        UUID pipelineId = pipeline.getId();

        assertRule(
                () -> new Deal(
                        pipelineId, aberta, 0, new DealDraft("X", null, null, BigDecimal.TEN, 1, null, null), TODAY),
                "AGENCY_OR_TENDER_REQUIRED");
        assertRule(
                () -> new Deal(
                        pipelineId,
                        aberta,
                        0,
                        new DealDraft("X", UUID.randomUUID(), null, BigDecimal.TEN, 4, null, null),
                        TODAY),
                "INVALID_PRIORITY");
        assertRule(
                () -> new Deal(
                        pipelineId,
                        aberta,
                        0,
                        new DealDraft("X", UUID.randomUUID(), null, new BigDecimal("-1"), 1, null, null),
                        TODAY),
                "MONEY_NEGATIVE");
        assertRule(() -> new Deal(pipelineId, aberta, 0, draft("  "), TODAY), "REQUIRED");

        Deal noValue = new Deal(
                pipelineId, aberta, 0, new DealDraft("Sem valor", UUID.randomUUID(), null, null, 1, null, null), TODAY);
        assertThat(noValue.getValue()).isEqualByComparingTo("0.00");
    }
}
