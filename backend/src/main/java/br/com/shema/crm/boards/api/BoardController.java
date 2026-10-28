package br.com.shema.crm.boards.api;

import br.com.shema.crm.boards.application.BoardService;
import br.com.shema.crm.boards.application.BoardService.BoardSummary;
import br.com.shema.crm.boards.application.BoardView;
import br.com.shema.crm.boards.domain.CardDraft;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/v1")
@Tag(name = "boards", description = "Quadros kanban")
class BoardController {

    record NameRequest(
            @NotBlank(message = "Informe o nome") @Size(max = 80)
            String name) {}

    record VersionedNameRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version,

            @NotBlank(message = "Informe o nome") @Size(max = 80)
            String name) {}

    record MoveColumnRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version,

            @NotNull @PositiveOrZero Integer position) {}

    record CardRequest(
            @NotBlank(message = "Informe o título") @Size(max = 200)
            String title,

            @Size(max = 2000) String notes,
            UUID ownerId,
            LocalDate dueDate,
            UUID tenderId) {

        CardDraft toDraft() {
            return new CardDraft(title, notes, ownerId, dueDate, tenderId);
        }
    }

    record NewCardRequest(
            @NotNull(message = "Escolha a coluna") UUID columnId,
            @NotNull @Valid CardRequest card) {}

    record UpdateCardRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version,

            @NotNull @Valid CardRequest card) {}

    record MoveCardRequest(
            @NotNull UUID columnId, @NotNull @PositiveOrZero Integer position) {}

    record BoardSummaryResponse(UUID id, String name, long cards) {}

    private final BoardService service;

    BoardController(BoardService service) {
        this.service = service;
    }

    @GetMapping("/boards")
    @Operation(summary = "Quadros", operationId = "listBoards")
    List<BoardSummaryResponse> list() {
        return service.list().stream()
                .map((BoardSummary b) -> new BoardSummaryResponse(b.id(), b.name(), b.cards()))
                .toList();
    }

    @PostMapping("/boards")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Cria um quadro", operationId = "createBoard")
    BoardView create(@Valid @RequestBody NameRequest request) {
        return service.create(request.name());
    }

    @GetMapping("/boards/{id}")
    @Operation(summary = "Um quadro com colunas e cards", operationId = "getBoard")
    BoardView get(@PathVariable UUID id) {
        return service.view(id);
    }

    @PutMapping("/boards/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Renomeia o quadro", operationId = "renameBoard")
    BoardView rename(@PathVariable UUID id, @Valid @RequestBody VersionedNameRequest request) {
        return service.rename(id, request.version(), request.name());
    }

    @DeleteMapping("/boards/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Apaga um quadro vazio", operationId = "deleteBoard")
    void delete(@PathVariable UUID id, @RequestParam long version) {
        service.delete(id, version);
    }

    @PostMapping("/boards/{id}/columns")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Adiciona uma coluna no fim", operationId = "addBoardColumn")
    BoardView addColumn(@PathVariable UUID id, @Valid @RequestBody VersionedNameRequest request) {
        return service.addColumn(id, request.version(), request.name());
    }

    @PutMapping("/boards/{id}/columns/{columnId}")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Renomeia uma coluna", operationId = "renameBoardColumn")
    BoardView renameColumn(
            @PathVariable UUID id, @PathVariable UUID columnId, @Valid @RequestBody VersionedNameRequest request) {
        return service.renameColumn(id, request.version(), columnId, request.name());
    }

    @PostMapping("/boards/{id}/columns/{columnId}/move")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Muda a coluna de posição", operationId = "moveBoardColumn")
    BoardView moveColumn(
            @PathVariable UUID id, @PathVariable UUID columnId, @Valid @RequestBody MoveColumnRequest request) {
        return service.moveColumn(id, request.version(), columnId, request.position());
    }

    @DeleteMapping("/boards/{id}/columns/{columnId}")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Exclui uma coluna vazia", operationId = "removeBoardColumn")
    BoardView removeColumn(@PathVariable UUID id, @PathVariable UUID columnId, @RequestParam long version) {
        return service.removeColumn(id, version, columnId);
    }

    @PostMapping("/boards/{id}/cards")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Cria um card no fim da coluna", operationId = "addBoardCard")
    BoardView addCard(@PathVariable UUID id, @Valid @RequestBody NewCardRequest request) {
        return service.addCard(id, request.columnId(), request.card().toDraft());
    }

    @PutMapping("/cards/{cardId}")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Altera um card", operationId = "updateBoardCard")
    BoardView updateCard(@PathVariable UUID cardId, @Valid @RequestBody UpdateCardRequest request) {
        return service.updateCard(cardId, request.version(), request.card().toDraft());
    }

    @PostMapping("/cards/{cardId}/move")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Move um card para outra coluna ou posição", operationId = "moveBoardCard")
    BoardView moveCard(@PathVariable UUID cardId, @Valid @RequestBody MoveCardRequest request) {
        return service.moveCard(cardId, request.columnId(), request.position());
    }

    @DeleteMapping("/cards/{cardId}")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Apaga um card", operationId = "deleteBoardCard")
    BoardView deleteCard(@PathVariable UUID cardId) {
        return service.deleteCard(cardId);
    }
}
