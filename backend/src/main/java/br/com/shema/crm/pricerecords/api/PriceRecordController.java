package br.com.shema.crm.pricerecords.api;

import br.com.shema.crm.pricerecords.application.PriceRecordService;
import br.com.shema.crm.pricerecords.application.PriceRecordViews.CaronaCheck;
import br.com.shema.crm.pricerecords.application.PriceRecordViews.Detail;
import br.com.shema.crm.pricerecords.application.PriceRecordViews.Event;
import br.com.shema.crm.pricerecords.application.PriceRecordViews.Highlight;
import br.com.shema.crm.pricerecords.application.PriceRecordViews.Prospect;
import br.com.shema.crm.pricerecords.application.PriceRecordViews.Summary;
import br.com.shema.crm.pricerecords.domain.PriceRecordDrafts.CaronaDraft;
import br.com.shema.crm.pricerecords.domain.PriceRecordDrafts.ItemDraft;
import br.com.shema.crm.pricerecords.domain.PriceRecordDrafts.LineDraft;
import br.com.shema.crm.pricerecords.domain.PriceRecordDrafts.RecordDraft;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
@RequestMapping("/api/v1/price-records")
@Tag(name = "price-records", description = "Atas de registro de preços e caronas")
class PriceRecordController {

    record RecordRequest(
            @NotBlank(message = "Informe o número da ata") @Size(max = 60)
            String number,

            LocalDate signedAt,

            @NotNull(message = "Informe a vigência") LocalDate validUntil,

            @NotNull Boolean allowsAdhesion,

            @Size(max = 2000) String notes,
            UUID documentId) {

        RecordDraft toDraft() {
            return new RecordDraft(number, signedAt, validUntil, allowsAdhesion, notes, documentId);
        }
    }

    /** @param suggestedByAi os dados vieram da leitura com IA e foram conferidos (fica no histórico) */
    record NewRecordRequest(
            @NotNull UUID tenderId, @NotNull @Valid RecordRequest record, Boolean suggestedByAi) {}

    record UpdateRecordRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version,

            @NotNull @Valid RecordRequest record,
            Boolean suggestedByAi) {}

    record AtaItemRequest(
            UUID id,
            @Size(max = 40) String lot,

            @NotBlank(message = "Descreva o item") @Size(max = 500)
            String description,

            @Size(max = 20) String unit,

            @NotNull BigDecimal quantity,
            @NotNull BigDecimal unitPrice) {}

    record ReplaceAtaItemsRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version,

            @NotNull @Size(max = 500) List<@Valid @NotNull AtaItemRequest> items,
            Boolean suggestedByAi) {}

    record LineRequest(@NotNull UUID itemId, @NotNull BigDecimal quantity) {}

    record CaronaRequest(
            @NotNull(message = "Escolha o órgão") UUID agencyId,
            @NotNull(message = "Informe a data da adesão") LocalDate adheredAt,
            @Size(max = 500) List<@Valid @NotNull LineRequest> lines,
            BigDecimal value,
            UUID dealId,
            @Size(max = 2000) String notes) {

        CaronaDraft toDraft() {
            return new CaronaDraft(
                    agencyId,
                    adheredAt,
                    lines == null
                            ? List.of()
                            : lines.stream()
                                    .map(l -> new LineDraft(l.itemId(), l.quantity()))
                                    .toList(),
                    value,
                    dealId,
                    notes);
        }
    }

    /** @param confirmLimits registra mesmo passando dos limites configurados (a carona fica marcada) */
    record RegisterCaronaRequest(@NotNull @Valid CaronaRequest carona, boolean confirmLimits) {}

    private final PriceRecordService service;

    PriceRecordController(PriceRecordService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Atas ordenadas pelo vencimento",
            description = "filter: VIGENTES (padrão), OPORTUNIDADES, VENCENDO, VENCIDAS ou TODAS.",
            operationId = "listPriceRecords")
    List<Summary> list(
            @RequestParam(required = false) PriceRecordService.Filter filter,
            @RequestParam(required = false) String q) {
        return service.list(filter, q);
    }

    @GetMapping("/highlights")
    @Operation(summary = "Ata de cada licitação pedida, para os destaques", operationId = "priceRecordHighlights")
    List<Highlight> highlights(@RequestParam @Size(max = 200) List<UUID> tenderIds) {
        return service.highlights(tenderIds);
    }

    @GetMapping("/by-tender/{tenderId}")
    @Operation(summary = "A ata de uma licitação (404 se ainda não tem)", operationId = "getPriceRecordByTender")
    Detail byTender(@PathVariable UUID tenderId) {
        return service.byTender(tenderId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Uma ata com itens e caronas", operationId = "getPriceRecord")
    Detail get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Registra a ata de uma licitação vencida", operationId = "createPriceRecord")
    Detail create(@Valid @RequestBody NewRecordRequest request) {
        return service.create(
                request.tenderId(), request.record().toDraft(), Boolean.TRUE.equals(request.suggestedByAi()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Altera os dados da ata", operationId = "updatePriceRecord")
    Detail update(@PathVariable UUID id, @Valid @RequestBody UpdateRecordRequest request) {
        return service.update(
                id, request.version(), request.record().toDraft(), Boolean.TRUE.equals(request.suggestedByAi()));
    }

    @PutMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Troca os itens registrados na ata", operationId = "replacePriceRecordItems")
    Detail replaceItems(@PathVariable UUID id, @Valid @RequestBody ReplaceAtaItemsRequest request) {
        return service.replaceItems(
                id,
                request.version(),
                request.items().stream()
                        .map(i ->
                                new ItemDraft(i.id(), i.lot(), i.description(), i.unit(), i.quantity(), i.unitPrice()))
                        .toList(),
                Boolean.TRUE.equals(request.suggestedByAi()));
    }

    @PostMapping("/{id}/caronas/check")
    @Operation(summary = "Confere uma carona sem registrar: valor e avisos de limite", operationId = "checkCarona")
    CaronaCheck check(@PathVariable UUID id, @Valid @RequestBody CaronaRequest request) {
        return service.checkCarona(id, request.toDraft());
    }

    @PostMapping("/{id}/caronas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(
            summary = "Registra uma carona",
            description = "Passou de um limite configurado: 422 CARONA_LIMITS, a menos que confirmLimits seja true.",
            operationId = "registerCarona")
    Detail register(@PathVariable UUID id, @Valid @RequestBody RegisterCaronaRequest request) {
        return service.registerCarona(id, request.carona().toDraft(), request.confirmLimits());
    }

    @DeleteMapping("/{id}/caronas/{caronaId}")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Apaga uma carona registrada por engano", operationId = "removeCarona")
    Detail removeCarona(@PathVariable UUID id, @PathVariable UUID caronaId, @RequestParam long version) {
        return service.removeCarona(id, caronaId, version);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Apaga uma ata sem caronas", operationId = "deletePriceRecord")
    void delete(@PathVariable UUID id, @RequestParam long version) {
        service.delete(id, version);
    }

    @GetMapping("/prospects")
    @Operation(
            summary = "Municípios sem ata: prefeituras sem licitação vencida e sem carona",
            operationId = "listProspects")
    List<Prospect> prospects(@RequestParam(required = false) String state, @RequestParam(required = false) String q) {
        return service.prospects(state, q);
    }

    @GetMapping("/history")
    @Operation(summary = "Histórico de ata, caronas e documentos de uma licitação", operationId = "priceRecordHistory")
    List<Event> history(@RequestParam UUID tenderId) {
        return service.history(tenderId);
    }
}
