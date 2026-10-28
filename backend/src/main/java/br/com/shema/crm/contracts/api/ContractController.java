package br.com.shema.crm.contracts.api;

import br.com.shema.crm.contracts.api.ContractDtos.CloseContractRequest;
import br.com.shema.crm.contracts.api.ContractDtos.ContractEventResponse;
import br.com.shema.crm.contracts.api.ContractDtos.FieldChangeResponse;
import br.com.shema.crm.contracts.api.ContractDtos.ItemRequest;
import br.com.shema.crm.contracts.api.ContractDtos.OpenContractsSummaryResponse;
import br.com.shema.crm.contracts.api.ContractDtos.PlaceOrderRequest;
import br.com.shema.crm.contracts.api.ContractDtos.ReplaceItemsRequest;
import br.com.shema.crm.contracts.api.ContractDtos.SignContractRequest;
import br.com.shema.crm.contracts.api.ContractDtos.TenderContractTotalsResponse;
import br.com.shema.crm.contracts.api.ContractDtos.UpdateContractRequest;
import br.com.shema.crm.contracts.application.ContractDetails;
import br.com.shema.crm.contracts.application.ContractService;
import br.com.shema.crm.contracts.infrastructure.ContractTotalsQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "contracts", description = "Contratos, itens, pedidos e saldo")
class ContractController {

    private final ContractService service;
    private final ContractTotalsQuery totals;
    private final JsonMapper json;

    ContractController(ContractService service, ContractTotalsQuery totals, JsonMapper json) {
        this.service = service;
        this.totals = totals;
        this.json = json;
    }

    @GetMapping("/tenders/{tenderId}/contracts")
    @Operation(summary = "Contratos de uma licitação, com itens, pedidos e saldo", operationId = "tenderContracts")
    List<ContractDetails> forTender(@PathVariable UUID tenderId) {
        return service.forTender(tenderId);
    }

    @PostMapping("/tenders/{tenderId}/contracts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Registra um contrato assinado (ou empenho, ou dispensa)", operationId = "signContract")
    ContractDetails sign(@PathVariable UUID tenderId, @Valid @RequestBody SignContractRequest request) {
        return service.sign(tenderId, request.contract().toDraft(), ItemRequest.drafts(request.items()));
    }

    @GetMapping("/tenders/{tenderId}/contracts/history")
    @Operation(summary = "Histórico dos contratos de uma licitação", operationId = "tenderContractsHistory")
    List<ContractEventResponse> history(@PathVariable UUID tenderId) {
        return service.history(tenderId).stream()
                .map(e -> new ContractEventResponse(
                        e.getId(),
                        e.getContractId(),
                        e.getType(),
                        e.getEventDate(),
                        e.getSummary(),
                        changes(e.getChanges()),
                        e.getUserName(),
                        e.getOccurredAt()))
                .toList();
    }

    @GetMapping("/contracts/{id}")
    @Operation(summary = "Um contrato", operationId = "getContract")
    ContractDetails get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/contracts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Altera os dados do contrato", operationId = "updateContract")
    ContractDetails update(@PathVariable UUID id, @Valid @RequestBody UpdateContractRequest request) {
        return service.update(id, request.version(), request.contract().toDraft());
    }

    @PutMapping("/contracts/{id}/items")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Substitui a lista de itens do contrato", operationId = "replaceContractItems")
    ContractDetails replaceItems(@PathVariable UUID id, @Valid @RequestBody ReplaceItemsRequest request) {
        return service.replaceItems(id, request.version(), ItemRequest.drafts(request.items()));
    }

    @PostMapping("/contracts/{id}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(
            summary = "Registra um pedido (aceita Idempotency-Key para não lançar duas vezes)",
            operationId = "placeContractOrder")
    ContractDetails placeOrder(
            @PathVariable UUID id,
            @RequestHeader(name = "Idempotency-Key", required = false) @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request) {
        return service.placeOrder(id, request.version(), request.toDraft(), idempotencyKey);
    }

    @DeleteMapping("/contracts/{id}/orders/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Apaga um pedido lançado por engano", operationId = "removeContractOrder")
    ContractDetails removeOrder(@PathVariable UUID id, @PathVariable UUID orderId, @RequestParam long version) {
        return service.removeOrder(id, version, orderId);
    }

    @PostMapping("/contracts/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Encerra o contrato", operationId = "closeContract")
    ContractDetails close(@PathVariable UUID id, @Valid @RequestBody CloseContractRequest request) {
        return service.close(id, request.version(), request.closedAt());
    }

    @GetMapping("/contracts/totals")
    @Operation(
            summary = "Valor, pedido e saldo dos contratos por licitação (para as listas)",
            operationId = "contractTotalsByTender")
    List<TenderContractTotalsResponse> totals(@RequestParam @Size(max = 200) Set<UUID> tenderIds) {
        return totals.byTender(tenderIds).stream()
                .map(t -> new TenderContractTotalsResponse(
                        t.tenderId(),
                        t.contracts(),
                        t.openContracts(),
                        t.value(),
                        t.ordered(),
                        t.remaining(),
                        t.lastOrderAt()))
                .toList();
    }

    @GetMapping("/contracts/summary")
    @Operation(
            summary = "Totais dos contratos abertos (saldo a pedir), para o painel",
            operationId = "openContractsSummary")
    OpenContractsSummaryResponse summary() {
        ContractTotalsQuery.OpenTotals open = totals.open();
        return new OpenContractsSummaryResponse(open.contracts(), open.value(), open.ordered(), open.remaining());
    }

    private List<FieldChangeResponse> changes(String changesJson) {
        if (changesJson == null) {
            return List.of();
        }
        return json.readValue(changesJson, new TypeReference<List<ContractService.FieldChange>>() {}).stream()
                .map(c -> new FieldChangeResponse(c.field(), c.from(), c.to()))
                .toList();
    }
}
