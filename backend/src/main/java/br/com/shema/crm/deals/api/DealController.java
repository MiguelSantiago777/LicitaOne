package br.com.shema.crm.deals.api;

import br.com.shema.crm.deals.application.DealFilter;
import br.com.shema.crm.deals.application.DealService;
import br.com.shema.crm.deals.application.PipelineView;
import br.com.shema.crm.deals.domain.DealDraft;
import br.com.shema.crm.deals.domain.DealStatus;
import br.com.shema.crm.deals.domain.StageKind;
import br.com.shema.crm.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/deals")
@Tag(name = "deals", description = "Funil de vendas (negociações)")
class DealController {

    record DealRequest(
            @NotBlank(message = "Informe o título") @Size(max = 200)
            String title,

            UUID agencyId,
            UUID tenderId,

            @NotNull(message = "Informe o valor") @DecimalMin("0.00") @Digits(integer = 13, fraction = 2)
            BigDecimal value,

            @NotNull @Min(1) @Max(3) Integer priority,

            UUID ownerId,

            @Size(max = 2000) String notes) {

        DealDraft toDraft() {
            return new DealDraft(title, agencyId, tenderId, value, priority, ownerId, notes);
        }
    }

    record NewDealRequest(UUID stageId, @NotNull @Valid DealRequest deal) {}

    record UpdateDealRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version,

            @NotNull @Valid DealRequest deal) {}

    record MoveDealRequest(
            @NotNull UUID stageId, @NotNull @PositiveOrZero Integer position) {}

    record NewStageRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version,

            @NotBlank(message = "Informe o nome") @Size(max = 60)
            String name,

            StageKind kind) {}

    record RenameStageRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version,

            @NotBlank(message = "Informe o nome") @Size(max = 60)
            String name) {}

    record MoveStageRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version,

            @NotNull @PositiveOrZero Integer position) {}

    private final DealService service;

    DealController(DealService service) {
        this.service = service;
    }

    @GetMapping("/pipeline")
    @Operation(
            summary = "Funil com etapas e negociações filtradas",
            description = "Contador e soma de cada etapa seguem o filtro. mine=true filtra as do usuário atual.",
            operationId = "getPipeline")
    PipelineView pipeline(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Set<DealStatus> status,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(required = false) UUID agencyId,
            @RequestParam(required = false) UUID tenderId) {
        UUID owner = mine ? CurrentUser.get().id() : null;
        return service.pipeline(new DealFilter(q, status, owner, agencyId, tenderId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Uma negociação", operationId = "getDeal")
    PipelineView.Deal get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(
            summary = "Cria uma negociação no fim da etapa",
            description = "Sem etapa, entra na primeira etapa aberta.",
            operationId = "createDeal")
    PipelineView.Deal create(@Valid @RequestBody NewDealRequest request) {
        return service.create(request.stageId(), request.deal().toDraft());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Altera uma negociação", operationId = "updateDeal")
    PipelineView.Deal update(@PathVariable UUID id, @Valid @RequestBody UpdateDealRequest request) {
        return service.update(id, request.version(), request.deal().toDraft());
    }

    @PostMapping("/{id}/move")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Move a negociação para outra etapa ou posição", operationId = "moveDeal")
    PipelineView.Deal move(@PathVariable UUID id, @Valid @RequestBody MoveDealRequest request) {
        return service.move(id, request.stageId(), request.position());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Apaga uma negociação sem tarefas abertas", operationId = "deleteDeal")
    void delete(@PathVariable UUID id, @RequestParam long version) {
        service.delete(id, version);
    }

    @PostMapping("/stages")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Adiciona uma etapa no fim do funil", operationId = "addDealStage")
    PipelineView addStage(@Valid @RequestBody NewStageRequest request) {
        return service.addStage(request.version(), request.name(), request.kind());
    }

    @PutMapping("/stages/{stageId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Renomeia uma etapa", operationId = "renameDealStage")
    PipelineView renameStage(@PathVariable UUID stageId, @Valid @RequestBody RenameStageRequest request) {
        return service.renameStage(request.version(), stageId, request.name());
    }

    @PostMapping("/stages/{stageId}/move")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Muda a etapa de posição", operationId = "moveDealStage")
    PipelineView moveStage(@PathVariable UUID stageId, @Valid @RequestBody MoveStageRequest request) {
        return service.moveStage(request.version(), stageId, request.position());
    }

    @DeleteMapping("/stages/{stageId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Exclui uma etapa vazia", operationId = "removeDealStage")
    PipelineView removeStage(@PathVariable UUID stageId, @RequestParam long version) {
        return service.removeStage(version, stageId);
    }
}
