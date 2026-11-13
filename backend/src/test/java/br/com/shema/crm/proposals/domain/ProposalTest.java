package br.com.shema.crm.proposals.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProposalTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
    private static final UUID OWNER = UUID.randomUUID();

    private static Proposal blank() {
        return Proposal.open(
                new ProposalNumber(2026, 7), TODAY, OWNER, new Proposal.Signer("Ana Souza", "Gerente", "52998224725"));
    }

    private static ProposalItemDraft item(String description, String quantity, String unitPrice) {
        return new ProposalItemDraft(
                null,
                description,
                "un",
                null,
                quantity == null ? null : new BigDecimal(quantity),
                unitPrice == null ? null : new BigDecimal(unitPrice));
    }

    private static ProposalDraft complete(List<ProposalItemDraft> items) {
        return new ProposalDraft(
                ProposalKind.PRODUTO,
                "Prefeitura de Magé",
                "11.222.333/0001-81",
                "Praça Nilo Peçanha, s/n",
                "compras@mage.rj.gov.br",
                "(21) 2633-0000",
                60,
                "30 dias após a entrega",
                "15 dias após o empenho",
                TODAY,
                "Ana Souza",
                "Gerente comercial",
                "529.982.247-25",
                items);
    }

    @Test
    void newProposalHasNumberDateAndSignerFromTheProfile() {
        Proposal p = blank();

        assertThat(p.number().text()).isEqualTo("2026/0007");
        assertThat(p.number().fileName()).isEqualTo("Proposta-2026-0007.pdf");
        assertThat(p.getProposalDate()).isEqualTo(TODAY);
        assertThat(p.getSignerName()).isEqualTo("Ana Souza");
        assertThat(p.getSignerCpf()).isEqualTo("52998224725");
        assertThat(p.missing()).contains("nome do cliente", "pelo menos um item", "validade da proposta");
    }

    @Test
    void totalsAreComputedAndTheNumberNeverChanges() {
        Proposal p = blank();

        p.update(complete(List.of(item("Papel A4", "10", "25.90"), item("Caneta", "3", "1.3333"))));

        assertThat(p.getItems()).hasSize(2);
        assertThat(p.getItems().get(0).total()).isEqualByComparingTo("259.00");
        assertThat(p.getItems().get(1).total()).isEqualByComparingTo("4.00");
        assertThat(p.getTotal()).isEqualByComparingTo("263.00");
        assertThat(p.getItems().get(1).displayNumber()).isEqualTo("2");
        assertThat(p.getClientCnpj()).isEqualTo("11222333000181");
        assertThat(p.missing()).isEmpty();
        assertThat(p.number().text()).isEqualTo("2026/0007");
    }

    @Test
    void halfTypedItemIsSavedButBlocksThePdf() {
        Proposal p = blank();

        p.update(complete(List.of(item("Papel A4", "10", "25.90"), item(null, "2", null))));

        assertThat(p.getTotal()).isEqualByComparingTo("259.00");
        assertThat(p.missing()).containsExactly("descrição, quantidade e valor unitário de todos os itens");
        assertThatThrownBy(p::requireReadyForDocument)
                .isInstanceOf(DomainRuleViolation.class)
                .hasMessageContaining("Para gerar o PDF, preencha");
    }

    @Test
    void invalidCnpjOrCpfIsRejected() {
        Proposal p = blank();
        ProposalDraft d = complete(List.of());
        ProposalDraft badCnpj = new ProposalDraft(
                d.kind(),
                d.clientName(),
                "11.222.333/0001-80",
                null,
                null,
                null,
                null,
                null,
                null,
                TODAY,
                null,
                null,
                null,
                List.of());

        assertThatThrownBy(() -> p.update(badCnpj)).hasMessageContaining("CNPJ inválido");
    }

    @Test
    void cancelledProposalCannotChangeUntilReopened() {
        Proposal p = blank();
        p.cancel(OWNER, Instant.parse("2026-11-13T12:00:00Z"));

        assertThatThrownBy(() -> p.update(complete(List.of()))).hasMessageContaining("cancelada");
        assertThatThrownBy(p::requireReadyForDocument).hasMessageContaining("cancelada");

        p.reopen();
        p.update(complete(List.of(item("Papel A4", "1", "10"))));
        assertThat(p.isCancelled()).isFalse();
    }

    @Test
    void duplicateGetsANewNumberAndTodayButKeepsTheContent() {
        Proposal p = blank();
        p.update(complete(List.of(item("Papel A4", "10", "25.90"))));

        Proposal copy = p.duplicate(new ProposalNumber(2026, 8), TODAY.plusDays(3), OWNER);

        assertThat(copy.getId()).isNotEqualTo(p.getId());
        assertThat(copy.number().text()).isEqualTo("2026/0008");
        assertThat(copy.getProposalDate()).isEqualTo(TODAY.plusDays(3));
        assertThat(copy.getClientName()).isEqualTo("Prefeitura de Magé");
        assertThat(copy.getItems()).hasSize(1);
        assertThat(copy.getItems().getFirst().getId())
                .isNotEqualTo(p.getItems().getFirst().getId());
        assertThat(copy.getTotal()).isEqualByComparingTo("259.00");
    }

    @Test
    void validityMustBePositive() {
        Proposal p = blank();
        ProposalDraft d = complete(List.of());
        ProposalDraft zero = new ProposalDraft(
                d.kind(), null, null, null, null, null, 0, null, null, TODAY, null, null, null, List.of());

        assertThatThrownBy(() -> p.update(zero)).hasMessageContaining("validade");
    }
}
