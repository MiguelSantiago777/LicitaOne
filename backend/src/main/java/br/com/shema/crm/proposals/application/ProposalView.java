package br.com.shema.crm.proposals.application;

import br.com.shema.crm.proposals.domain.Proposal;
import br.com.shema.crm.proposals.domain.ProposalItem;
import br.com.shema.crm.proposals.domain.ProposalKind;
import br.com.shema.crm.shared.domain.AmountInWords;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A proposta inteira, para a tela de edição. {@code missing} é o que falta para gerar o PDF. */
@Schema(name = "ProposalResponse")
public record ProposalView(
        UUID id,
        String number,
        String fileName,
        ProposalKind kind,
        String clientName,
        String clientCnpj,
        String clientAddress,
        String clientEmail,
        String clientPhones,
        Integer validityDays,
        String paymentTerms,
        String deliveryTerms,
        LocalDate proposalDate,
        String signerName,
        String signerRole,
        String signerCpf,
        List<ProposalItemView> items,
        BigDecimal total,
        String totalInWords,
        List<String> missing,
        Instant cancelledAt,
        Instant createdAt,
        long version,
        boolean canEdit) {

    @Schema(name = "ProposalItemResponse")
    public record ProposalItemView(
            UUID id,
            int position,
            String itemNumber,
            String description,
            String unit,
            String brand,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal total) {}

    static ProposalView from(Proposal p, boolean canWrite) {
        return new ProposalView(
                p.getId(),
                p.number().text(),
                p.number().fileName(),
                p.getKind(),
                p.getClientName(),
                p.getClientCnpj(),
                p.getClientAddress(),
                p.getClientEmail(),
                p.getClientPhones(),
                p.getValidityDays(),
                p.getPaymentTerms(),
                p.getDeliveryTerms(),
                p.getProposalDate(),
                p.getSignerName(),
                p.getSignerRole(),
                p.getSignerCpf(),
                p.getItems().stream().map(ProposalView::item).toList(),
                p.getTotal(),
                AmountInWords.reais(p.getTotal()),
                p.missing(),
                p.getCancelledAt(),
                p.getCreatedAt(),
                p.getVersion() == null ? 0 : p.getVersion(),
                canWrite && !p.isCancelled());
    }

    private static ProposalItemView item(ProposalItem i) {
        return new ProposalItemView(
                i.getId(),
                i.getPosition(),
                i.getItemNumber(),
                i.getDescription(),
                i.getUnit(),
                i.getBrand(),
                i.getQuantity(),
                i.getUnitPrice(),
                i.total());
    }
}
