package br.com.shema.crm.tenders.api;

import br.com.shema.crm.identity.UserDirectory;
import br.com.shema.crm.identity.UserSummary;
import br.com.shema.crm.shared.security.CurrentUser;
import br.com.shema.crm.shared.web.PageResponse;
import br.com.shema.crm.tenders.api.TenderDtos.AgencyTotalResponse;
import br.com.shema.crm.tenders.api.TenderDtos.CreateTenderRequest;
import br.com.shema.crm.tenders.api.TenderDtos.StatusTotalResponse;
import br.com.shema.crm.tenders.api.TenderDtos.SuggestionsResponse;
import br.com.shema.crm.tenders.api.TenderDtos.TenderEventResponse;
import br.com.shema.crm.tenders.api.TenderDtos.TenderResponse;
import br.com.shema.crm.tenders.api.TenderDtos.TransitionRequest;
import br.com.shema.crm.tenders.api.TenderDtos.UpdateTenderRequest;
import br.com.shema.crm.tenders.application.TenderFilter;
import br.com.shema.crm.tenders.application.TenderService;
import br.com.shema.crm.tenders.domain.Tender;
import br.com.shema.crm.tenders.domain.TenderStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1/tenders")
@Tag(name = "tenders", description = "Licitações")
class TenderController {

    /** Campos pelos quais a lista pode ser ordenada (nome na API → caminho na entidade). */
    private static final Map<String, String> SORTABLE = Map.of(
            "value", "totalValue",
            "agency", "agency.name",
            "wonAt", "wonAt",
            "signedAt", "signedAt",
            "closedAt", "closedAt",
            "sessionAt", "sessionAt",
            "createdAt", "createdAt");

    private final TenderService service;
    private final UserDirectory userDirectory;
    private final JsonMapper json;

    TenderController(TenderService service, UserDirectory userDirectory, JsonMapper json) {
        this.service = service;
        this.userDirectory = userDirectory;
        this.json = json;
    }

    @GetMapping
    @Operation(summary = "Lista licitações com filtros, ordenação e paginação", operationId = "searchTenders")
    PageResponse<TenderResponse> search(
            @RequestParam(required = false) Set<TenderStatus> status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID agencyId,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String materialType,
            @RequestParam(required = false) BigDecimal minValue,
            @RequestParam(required = false) BigDecimal maxValue,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate signedBefore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "value") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        TenderFilter filter = new TenderFilter(
                status, q, agencyId, city, state, materialType, minValue, maxValue, from, to, ownerId, signedBefore);
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort order = Sort.by(dir, SORTABLE.getOrDefault(sort, "totalValue")).and(Sort.by("id"));
        Page<Tender> result =
                service.search(filter, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), order));
        Map<UUID, UserSummary> owners = owners(result.getContent());
        CurrentUser user = CurrentUser.get();
        return PageResponse.of(
                result, t -> TenderResponse.from(t, owners.get(t.getOwnerId()), user.canEditOwnedBy(t.getOwnerId())));
    }

    @GetMapping("/summary")
    @Operation(
            summary = "Quantidade e valor por situação (segue os mesmos filtros da lista)",
            operationId = "tenderSummary")
    List<StatusTotalResponse> summary(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID agencyId,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String materialType,
            @RequestParam(required = false) BigDecimal minValue,
            @RequestParam(required = false) BigDecimal maxValue,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID ownerId) {
        TenderFilter filter = new TenderFilter(
                null, q, agencyId, city, state, materialType, minValue, maxValue, from, to, ownerId, null);
        return service.summary(filter).stream()
                .map(s -> new StatusTotalResponse(s.status(), s.status().label(), s.count(), s.value()))
                .toList();
    }

    @GetMapping("/top-agencies")
    @Operation(
            summary = "Órgãos pelo valor das licitações vencidas, do maior para o menor (painel)",
            operationId = "topAgencies")
    List<AgencyTotalResponse> topAgencies(@RequestParam(defaultValue = "5") int limit) {
        return service.topAgencies(limit).stream()
                .map(a -> new AgencyTotalResponse(
                        a.getAgencyId(), a.getName(), a.getCity(), a.getState(), a.getTenders(), a.getValue()))
                .toList();
    }

    @GetMapping("/suggestions")
    @Operation(summary = "Tipos de material e portais já usados, para autocompletar", operationId = "tenderSuggestions")
    SuggestionsResponse suggestions() {
        Map<String, List<String>> s = service.suggestions();
        return new SuggestionsResponse(s.get("materialTypes"), s.get("portals"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Uma licitação", operationId = "getTender")
    TenderResponse get(@PathVariable UUID id) {
        return response(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Cadastra uma licitação (em disputa ou já vencida)", operationId = "createTender")
    TenderResponse create(@Valid @RequestBody CreateTenderRequest request) {
        return response(service.register(request.tender().toInput(), request.initialStatus(), request.wonAt()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Altera os dados de uma licitação", operationId = "updateTender")
    TenderResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTenderRequest request) {
        return response(service.update(id, request.version(), request.tender().toInput()));
    }

    @PostMapping("/{id}/transitions")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Muda a situação da licitação", operationId = "transitionTender")
    TenderResponse transition(@PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
        return response(service.transition(id, request.version(), request.target(), request.date()));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Histórico de alterações", operationId = "tenderHistory")
    List<TenderEventResponse> history(@PathVariable UUID id) {
        return service.history(id).stream()
                .map(e -> new TenderEventResponse(
                        e.getId(),
                        e.getType(),
                        e.getFromStatus(),
                        e.getFromStatus() == null ? null : e.getFromStatus().label(),
                        e.getToStatus(),
                        e.getToStatus() == null ? null : e.getToStatus().label(),
                        e.getEventDate(),
                        changes(e.getChanges()),
                        e.getUserName(),
                        e.getOccurredAt()))
                .toList();
    }

    private List<TenderDtos.FieldChangeResponse> changes(String changesJson) {
        if (changesJson == null) {
            return List.of();
        }
        return json.readValue(changesJson, new TypeReference<List<TenderService.FieldChange>>() {}).stream()
                .map(c -> new TenderDtos.FieldChangeResponse(c.field(), c.from(), c.to()))
                .toList();
    }

    private TenderResponse response(Tender t) {
        UserSummary owner = userDirectory.find(t.getOwnerId()).orElse(null);
        return TenderResponse.from(t, owner, CurrentUser.get().canEditOwnedBy(t.getOwnerId()));
    }

    private Map<UUID, UserSummary> owners(List<Tender> tenders) {
        Set<UUID> ids = tenders.stream()
                .map(Tender::getOwnerId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        return userDirectory.findAll(ids);
    }
}
