package br.com.shema.crm.tenders.api;

import br.com.shema.crm.identity.UserSummary;
import br.com.shema.crm.tenders.application.TenderService;
import br.com.shema.crm.tenders.domain.Modality;
import br.com.shema.crm.tenders.domain.Tender;
import br.com.shema.crm.tenders.domain.TenderEvent;
import br.com.shema.crm.tenders.domain.TenderStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

final class TenderDtos {

    private TenderDtos() {}

    record TenderRequest(
            @NotNull(message = "Escolha o órgão") UUID agencyId,
            Modality modality,
            @Size(max = 10) String uasg,
            @Size(max = 30) String processNumber,
            @Size(max = 80) String portal,
            OffsetDateTime sessionAt,

            @NotBlank(message = "Descreva o objeto da licitação") @Size(max = 2000)
            String object,

            @NotBlank(message = "Informe o tipo de material") @Size(max = 120)
            String materialType,

            @PositiveOrZero(message = "Quantidade não pode ser negativa")
            Integer itemsQuantity,

            @NotNull(message = "Informe o valor")
            @PositiveOrZero(message = "Valor não pode ser negativo")
            @Digits(integer = 13, fraction = 2, message = "Valor com no máximo 2 casas decimais")
            BigDecimal totalValue,

            UUID ownerId,
            @Size(max = 4000) String notes) {

        TenderService.TenderInput toInput() {
            return new TenderService.TenderInput(
                    agencyId,
                    modality,
                    uasg,
                    processNumber,
                    portal,
                    sessionAt,
                    object,
                    materialType,
                    itemsQuantity,
                    totalValue,
                    ownerId,
                    notes);
        }
    }

    record CreateTenderRequest(
            @NotNull(message = "Escolha a situação inicial") TenderStatus initialStatus,
            LocalDate wonAt,
            @NotNull @jakarta.validation.Valid TenderRequest tender) {}

    record UpdateTenderRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version,

            @NotNull @jakarta.validation.Valid TenderRequest tender) {}

    record TransitionRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version,

            @NotNull(message = "Escolha a nova situação") TenderStatus target,
            LocalDate date) {}

    record OwnerResponse(UUID id, String name, String initials) {
        static OwnerResponse from(UserSummary u) {
            return u == null ? null : new OwnerResponse(u.id(), u.name(), u.initials());
        }
    }

    record AgencyRefResponse(UUID id, String name, String city, String state) {}

    record NextStatusResponse(TenderStatus status, String label, String requiredDate) {}

    record TenderResponse(
            UUID id,
            AgencyRefResponse agency,
            Modality modality,
            String modalityLabel,
            String uasg,
            String processNumber,
            String reference,
            String portal,
            OffsetDateTime sessionAt,
            String object,
            String materialType,
            Integer itemsQuantity,
            BigDecimal totalValue,
            TenderStatus status,
            String statusLabel,
            LocalDate wonAt,
            LocalDate signedAt,
            LocalDate ordersStartedAt,
            LocalDate closedAt,
            OwnerResponse owner,
            String notes,
            List<NextStatusResponse> nextStatuses,
            boolean canEdit,
            long version,
            Instant createdAt,
            Instant updatedAt) {

        static TenderResponse from(Tender t, UserSummary owner, boolean canEdit) {
            List<NextStatusResponse> next = t.getStatus().next().stream()
                    .filter(s -> !s.isManagedByContracts())
                    .sorted()
                    .map(s -> new NextStatusResponse(
                            s,
                            s.label(),
                            s.requiredDate() == null ? null : s.requiredDate().label()))
                    .toList();
            return new TenderResponse(
                    t.getId(),
                    new AgencyRefResponse(
                            t.getAgency().getId(),
                            t.getAgency().getName(),
                            t.getAgency().getCity(),
                            t.getAgency().getState()),
                    t.getModality(),
                    t.getModality().label(),
                    t.getUasg() == null ? null : t.getUasg().value(),
                    t.getProcessNumber() == null ? null : t.getProcessNumber().value(),
                    t.reference(),
                    t.getPortal(),
                    t.getSessionAt(),
                    t.getObject(),
                    t.getMaterialType(),
                    t.getItemsQuantity(),
                    t.getTotalValue().amount(),
                    t.getStatus(),
                    t.getStatus().label(),
                    t.getWonAt(),
                    t.getSignedAt(),
                    t.getOrdersStartedAt(),
                    t.getClosedAt(),
                    OwnerResponse.from(owner),
                    t.getNotes(),
                    next,
                    canEdit,
                    t.getVersion() == null ? 0 : t.getVersion(),
                    t.getCreatedAt(),
                    t.getUpdatedAt());
        }
    }

    /** @param value soma das licitações vencidas do órgão */
    record AgencyTotalResponse(UUID agencyId, String name, String city, String state, long tenders, BigDecimal value) {}

    record StatusTotalResponse(TenderStatus status, String label, long count, BigDecimal value) {}

    record TenderEventResponse(
            UUID id,
            TenderEvent.Type type,
            TenderStatus fromStatus,
            String fromLabel,
            TenderStatus toStatus,
            String toLabel,
            LocalDate eventDate,
            List<FieldChangeResponse> changes,
            String userName,
            Instant occurredAt) {}

    record FieldChangeResponse(String field, String from, String to) {}

    record SuggestionsResponse(List<String> materialTypes, List<String> portals) {}
}
