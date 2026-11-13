package br.com.shema.crm.proposals.api;

import br.com.shema.crm.proposals.application.ProposalFilter;
import br.com.shema.crm.proposals.application.ProposalService;
import br.com.shema.crm.proposals.application.ProposalSummary;
import br.com.shema.crm.proposals.application.ProposalView;
import br.com.shema.crm.proposals.domain.ProposalDraft;
import br.com.shema.crm.proposals.domain.ProposalItemDraft;
import br.com.shema.crm.proposals.domain.ProposalKind;
import br.com.shema.crm.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/proposals")
@Tag(name = "proposals", description = "Propostas de preços")
class ProposalController {

    record ProposalItemRequest(
            @Size(max = 20) String itemNumber,
            @Size(max = 2000) String description,
            @Size(max = 20) String unit,
            @Size(max = 100) String brand,
            BigDecimal quantity,
            BigDecimal unitPrice) {

        ProposalItemDraft toDraft() {
            return new ProposalItemDraft(itemNumber, description, unit, brand, quantity, unitPrice);
        }
    }

    record ProposalRequest(
            @NotNull(message = "Escolha produto ou serviço") ProposalKind kind,

            @Size(max = 200) String clientName,
            @Size(max = 30) String clientCnpj,
            @Size(max = 300) String clientAddress,
            @Size(max = 254) String clientEmail,
            @Size(max = 120) String clientPhones,
            Integer validityDays,
            @Size(max = 500) String paymentTerms,
            @Size(max = 500) String deliveryTerms,

            @NotNull(message = "Informe a data da proposta") LocalDate proposalDate,

            @Size(max = 120) String signerName,
            @Size(max = 120) String signerRole,
            @Size(max = 20) String signerCpf,

            @NotNull @Size(max = 500) List<@NotNull @Valid ProposalItemRequest> items) {

        ProposalDraft toDraft() {
            return new ProposalDraft(
                    kind,
                    clientName,
                    clientCnpj,
                    clientAddress,
                    clientEmail,
                    clientPhones,
                    validityDays,
                    paymentTerms,
                    deliveryTerms,
                    proposalDate,
                    signerName,
                    signerRole,
                    signerCpf,
                    items.stream().map(ProposalItemRequest::toDraft).toList());
        }
    }

    record UpdateProposalRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version,

            @NotNull @Valid ProposalRequest proposal) {}

    record ProposalVersionRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version) {}

    record ProposalCompanyWarningResponse(List<String> missing) {}

    private final ProposalService service;

    ProposalController(ProposalService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Propostas, da mais recente para a mais antiga",
            description = "q acha pelo número (2026/0001 ou 1), pelo nome do cliente ou pelo CNPJ.",
            operationId = "searchProposals")
    PageResponse<ProposalSummary> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ProposalKind kind,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean includeCancelled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Sort order = Sort.by(Sort.Direction.DESC, "numberYear", "numberSeq");
        return PageResponse.of(
                service.search(
                        new ProposalFilter(q, kind, from, to, includeCancelled),
                        PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), order)),
                p -> p);
    }

    @GetMapping("/company-warning")
    @Operation(summary = "Dados da empresa que faltam no cabeçalho do PDF", operationId = "proposalCompanyWarning")
    ProposalCompanyWarningResponse companyWarning() {
        return new ProposalCompanyWarningResponse(service.missingCompanyData());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Uma proposta, com os itens", operationId = "getProposal")
    ProposalView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(
            summary = "Cria uma proposta em branco, já com o número",
            description = "O número (2026/0001) é dado agora e nunca muda.",
            operationId = "createProposal")
    ProposalView create() {
        return service.create();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Salva a proposta inteira (cabeçalho e itens)", operationId = "updateProposal")
    ProposalView update(@PathVariable UUID id, @Valid @RequestBody UpdateProposalRequest request) {
        return service.update(id, request.version(), request.proposal().toDraft());
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Cria uma cópia com número novo e a data de hoje", operationId = "duplicateProposal")
    ProposalView duplicate(@PathVariable UUID id) {
        return service.duplicate(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Cancela a proposta (continua na lista, com o número)", operationId = "cancelProposal")
    ProposalView cancel(@PathVariable UUID id, @Valid @RequestBody ProposalVersionRequest request) {
        return service.cancel(id, request.version());
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Desfaz o cancelamento", operationId = "reopenProposal")
    ProposalView reopen(@PathVariable UUID id, @Valid @RequestBody ProposalVersionRequest request) {
        return service.reopen(id, request.version());
    }

    // Sem "produces": com ele, o 422 (problem+json) da proposta incompleta viraria 406
    @GetMapping("/{id}/pdf")
    @Operation(
            summary = "PDF da proposta, sem assinatura",
            description = "422 com o que falta preencher se a proposta estiver incompleta ou cancelada.",
            operationId = "downloadProposalPdf")
    @ApiResponse(
            responseCode = "200",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PDF_VALUE,
                            schema = @Schema(type = "string", format = "binary")))
    ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        ProposalService.Document file = service.pdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.content());
    }
}
