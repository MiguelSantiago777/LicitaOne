package br.com.shema.crm.contracts.api;

import br.com.shema.crm.contracts.domain.ContractDraft;
import br.com.shema.crm.contracts.domain.ContractEvent;
import br.com.shema.crm.contracts.domain.ContractKind;
import br.com.shema.crm.contracts.domain.ItemDraft;
import br.com.shema.crm.contracts.domain.OrderDraft;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class ContractDtos {

    private ContractDtos() {}

    record ContractRequest(
            ContractKind kind,
            @Size(max = 40) String number,

            @NotNull(message = "Informe a data de assinatura")
            LocalDate signedAt,

            LocalDate validUntil,

            @NotNull(message = "Informe o valor do contrato")
            @PositiveOrZero(message = "Valor não pode ser negativo")
            @Digits(integer = 13, fraction = 2, message = "Valor com no máximo 2 casas decimais")
            BigDecimal value,

            @Size(max = 4000) String notes) {

        ContractDraft toDraft() {
            return new ContractDraft(kind, number, signedAt, validUntil, value, notes);
        }
    }

    record ItemRequest(
            UUID id,
            @Size(max = 40) String lot,

            @NotBlank(message = "Descreva o item") @Size(max = 500)
            String description,

            @Size(max = 20) String unit,

            @NotNull(message = "Informe a quantidade")
            @Positive(message = "Quantidade deve ser maior que zero")
            @Digits(integer = 12, fraction = 3)
            BigDecimal quantity,

            @NotNull(message = "Informe o valor unitário") @PositiveOrZero @Digits(integer = 11, fraction = 4)
            BigDecimal unitPrice) {

        ItemDraft toDraft() {
            return new ItemDraft(id, lot, description, unit, quantity, unitPrice);
        }

        static List<ItemDraft> drafts(List<ItemRequest> items) {
            return items == null
                    ? List.of()
                    : items.stream().map(ItemRequest::toDraft).toList();
        }
    }

    /** @param suggestedByAi os dados vieram da leitura com IA e foram conferidos (fica no histórico) */
    record SignContractRequest(
            @NotNull @Valid ContractRequest contract,
            @Valid @Size(max = 500) List<ItemRequest> items,
            Boolean suggestedByAi) {}

    record UpdateContractRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version,

            @NotNull @Valid ContractRequest contract,
            Boolean suggestedByAi) {}

    record ReplaceItemsRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version,

            @NotNull @Valid @Size(max = 500) List<ItemRequest> items,
            Boolean suggestedByAi) {}

    record OrderLineRequest(
            @NotNull UUID itemId,

            @NotNull(message = "Informe a quantidade") @Positive @Digits(integer = 12, fraction = 3)
            BigDecimal quantity) {}

    record PlaceOrderRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version,

            @NotNull(message = "Informe a data do pedido") LocalDate orderedAt,
            @Size(max = 60) String reference,
            @Size(max = 2000) String notes,
            @Positive @Digits(integer = 13, fraction = 2) BigDecimal value,
            @Valid @Size(max = 500) List<OrderLineRequest> lines,
            UUID sourceDocumentId,
            Boolean suggestedByAi) {

        OrderDraft toDraft() {
            List<OrderDraft.Line> l = lines == null
                    ? List.of()
                    : lines.stream()
                            .map(x -> new OrderDraft.Line(x.itemId(), x.quantity()))
                            .toList();
            return new OrderDraft(orderedAt, reference, notes, value, l, sourceDocumentId);
        }
    }

    record CloseContractRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version,

            @NotNull(message = "Informe a data de encerramento")
            LocalDate closedAt) {}

    record FieldChangeResponse(String field, String from, String to) {}

    record ContractEventResponse(
            UUID id,
            UUID contractId,
            ContractEvent.Type type,
            LocalDate eventDate,
            String summary,
            List<FieldChangeResponse> changes,
            String userName,
            Instant occurredAt) {}

    record TenderContractTotalsResponse(
            UUID tenderId,
            long contracts,
            long openContracts,
            BigDecimal value,
            BigDecimal ordered,
            BigDecimal remaining,
            LocalDate lastOrderAt) {}

    record OpenContractsSummaryResponse(long contracts, BigDecimal value, BigDecimal ordered, BigDecimal remaining) {}

    /** @param total quantos contratos estão nessa situação (a lista traz só os mais antigos) */
    record ContractsWithoutOrderResponse(
            int days, long total, BigDecimal value, List<ContractWithoutOrderResponse> items) {}

    record ContractWithoutOrderResponse(
            UUID contractId,
            UUID tenderId,
            String agencyName,
            String identification,
            String kind,
            String number,
            LocalDate signedAt,
            long daysSinceSigned,
            BigDecimal value) {}
}
