package br.com.shema.crm.proposals.application;

import br.com.shema.crm.proposals.domain.Proposal;
import br.com.shema.crm.proposals.domain.ProposalKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Uma linha da lista de propostas: sem os itens (não são lidos), com o total guardado na proposta. */
@Schema(name = "ProposalSummaryResponse")
public record ProposalSummary(
        UUID id,
        String number,
        LocalDate proposalDate,
        ProposalKind kind,
        String clientName,
        String clientCnpj,
        BigDecimal total,
        String signerName,
        Instant cancelledAt) {

    static ProposalSummary from(Proposal p) {
        return new ProposalSummary(
                p.getId(),
                p.number().text(),
                p.getProposalDate(),
                p.getKind(),
                p.getClientName(),
                p.getClientCnpj(),
                p.getTotal(),
                p.getSignerName(),
                p.getCancelledAt());
    }
}
