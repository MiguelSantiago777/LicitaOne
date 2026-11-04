package br.com.shema.crm.tenders.api;

import br.com.shema.crm.shared.security.CurrentUser;
import br.com.shema.crm.tenders.application.TenderContactService;
import br.com.shema.crm.tenders.application.TenderContactService.ContactInput;
import br.com.shema.crm.tenders.domain.TenderContact;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenders/{tenderId}/contacts")
@Tag(name = "tenders", description = "Licitações")
class TenderContactController {

    private final TenderContactService service;

    TenderContactController(TenderContactService service) {
        this.service = service;
    }

    /** @param suggestedByAi a situação e o resumo vieram da IA e foram conferidos (fica no histórico) */
    @Schema(name = "TenderContactRequest")
    record ContactRequest(
            UUID contractId,
            @NotNull(message = "Informe o canal do contato") TenderContact.Channel channel,
            TenderContact.Outcome outcome,
            @NotNull(message = "Informe a data do contato") LocalDate contactedAt,

            @NotNull(message = "Escreva o que foi conversado") @Size(max = TenderContact.MAX_SUMMARY)
            String summary,

            Boolean suggestedByAi) {}

    @Schema(name = "TenderContactResponse")
    record ContactResponse(
            UUID id,
            UUID contractId,
            TenderContact.Channel channel,
            String channelLabel,
            TenderContact.Outcome outcome,
            String outcomeLabel,
            LocalDate contactedAt,
            String summary,
            boolean suggestedByAi,
            String userName,
            Instant createdAt,
            boolean canRemove) {

        static ContactResponse of(TenderContact c, CurrentUser user, boolean canEditTender) {
            return new ContactResponse(
                    c.getId(),
                    c.getContractId(),
                    c.getChannel(),
                    c.getChannel().label(),
                    c.getOutcome(),
                    c.getOutcome() == null ? null : c.getOutcome().label(),
                    c.getContactedAt(),
                    c.getSummary(),
                    c.isSuggestedByAi(),
                    c.getUserName(),
                    c.getCreatedAt(),
                    canEditTender && (user.isAdmin() || c.getUserId().equals(user.id())));
        }
    }

    @GetMapping
    @Operation(summary = "Contatos com o órgão registrados na licitação", operationId = "tenderContacts")
    List<ContactResponse> list(@PathVariable UUID tenderId) {
        CurrentUser user = CurrentUser.get();
        List<TenderContact> contacts = service.list(tenderId);
        boolean canEdit = service.canEdit(tenderId, user);
        return contacts.stream().map(c -> ContactResponse.of(c, user, canEdit)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Registra um contato com o órgão", operationId = "registerTenderContact")
    ContactResponse register(@PathVariable UUID tenderId, @Valid @RequestBody ContactRequest request) {
        TenderContact saved = service.register(
                tenderId,
                new ContactInput(
                        request.contractId(),
                        request.channel(),
                        request.outcome(),
                        request.contactedAt(),
                        request.summary(),
                        Boolean.TRUE.equals(request.suggestedByAi())));
        return ContactResponse.of(saved, CurrentUser.get(), true);
    }

    @DeleteMapping("/{contactId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Apaga um contato registrado por engano", operationId = "removeTenderContact")
    void remove(@PathVariable UUID tenderId, @PathVariable UUID contactId) {
        service.remove(tenderId, contactId);
    }
}
