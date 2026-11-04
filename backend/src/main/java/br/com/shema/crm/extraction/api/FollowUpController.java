package br.com.shema.crm.extraction.api;

import br.com.shema.crm.extraction.application.FollowUpAssistant;
import br.com.shema.crm.extraction.application.FollowUpAssistant.FollowUpView;
import br.com.shema.crm.extraction.application.FollowUpAssistant.ResponseView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenders/{tenderId}")
@Tag(name = "extractions", description = "Leitura de atas, contratos e pedidos com IA")
class FollowUpController {

    private final FollowUpAssistant assistant;

    FollowUpController(FollowUpAssistant assistant) {
        this.assistant = assistant;
    }

    /** @param channel rótulo do canal ("WhatsApp", "E-mail"...), para o contexto */
    @Schema(name = "AgencyResponseRequest")
    record InterpretRequest(
            @NotNull(message = "Cole a resposta do órgão") @Size(max = FollowUpAssistant.MAX_TEXT + 1000)
            String text,

            LocalDate contactedAt,
            @Size(max = 40) String channel) {}

    /** @param tone NORMAL, CURTO ou FORMAL */
    @Schema(name = "FollowUpDraftRequest")
    record FollowUpRequest(
            UUID contractId,
            @NotNull @Pattern(regexp = "EMAIL|WHATSAPP") String channel,
            @Pattern(regexp = "NORMAL|CURTO|FORMAL") String tone) {}

    @PostMapping("/contacts/interpretation")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(
            summary =
                    "Entende com IA a resposta do órgão (situação, data prevista, resumo, próxima ação); não grava nada",
            operationId = "interpretAgencyResponse")
    ResponseView interpret(@PathVariable UUID tenderId, @Valid @RequestBody InterpretRequest request) {
        return assistant.interpret(tenderId, request.text(), request.contactedAt(), request.channel());
    }

    @PostMapping("/follow-up-draft")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(
            summary = "Escreve com IA um follow-up ao órgão com o contexto do contrato; não envia nem grava",
            operationId = "writeFollowUpDraft")
    FollowUpView followUp(@PathVariable UUID tenderId, @Valid @RequestBody FollowUpRequest request) {
        return assistant.followUp(tenderId, request.contractId(), "WHATSAPP".equals(request.channel()), request.tone());
    }
}
