package br.com.shema.crm.tasks.api;

import br.com.shema.crm.shared.security.CurrentUser;
import br.com.shema.crm.shared.web.PageResponse;
import br.com.shema.crm.tasks.application.TaskFilter;
import br.com.shema.crm.tasks.application.TaskService;
import br.com.shema.crm.tasks.application.TaskView;
import br.com.shema.crm.tasks.domain.TaskDraft;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
@RequestMapping("/api/v1/tasks")
@Tag(name = "tasks", description = "Tarefas")
class TaskController {

    record TaskRequest(
            @NotBlank(message = "Informe o título") @Size(max = 200)
            String title,

            @Size(max = 2000) String notes,

            @NotNull(message = "Informe a data e a hora") OffsetDateTime dueAt,

            UUID ownerId,
            UUID dealId,
            UUID tenderId) {

        TaskDraft toDraft() {
            return new TaskDraft(title, notes, dueAt, ownerId, dealId, tenderId);
        }
    }

    record UpdateTaskRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version,

            @NotNull @Valid TaskRequest task) {}

    record VersionRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version) {}

    record CompleteTasksRequest(
            @NotEmpty(message = "Escolha pelo menos uma tarefa") @Size(max = TaskService.MAX_BULK)
            List<@NotNull UUID> ids) {}

    record CompleteTasksResponse(int completed) {}

    record TaskSummaryResponse(long open, long overdue) {}

    private final TaskService service;

    TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Tarefas com filtros, ordenadas pelo vencimento",
            description = "situation: ABERTAS (padrão, inclui as atrasadas), ATRASADAS, CONCLUIDAS ou TODAS. "
                    + "mine=true filtra as do usuário atual.",
            operationId = "searchTasks")
    PageResponse<TaskView> search(
            @RequestParam(required = false) TaskFilter.Situation situation,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) UUID dealId,
            @RequestParam(required = false) UUID tenderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "asc") String direction) {
        UUID owner = mine ? CurrentUser.get().id() : ownerId;
        Sort.Direction dir = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort order = Sort.by(dir, "dueAt").and(Sort.by("id"));
        return PageResponse.of(
                service.search(
                        new TaskFilter(situation, q, owner, dealId, tenderId),
                        PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), order)),
                t -> t);
    }

    @GetMapping("/summary")
    @Operation(summary = "Quantas tarefas abertas e atrasadas", operationId = "taskSummary")
    TaskSummaryResponse summary(@RequestParam(defaultValue = "false") boolean mine) {
        TaskService.Summary s = service.summary(mine ? CurrentUser.get().id() : null);
        return new TaskSummaryResponse(s.open(), s.overdue());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Uma tarefa", operationId = "getTask")
    TaskView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Cria uma tarefa", operationId = "createTask")
    TaskView create(@Valid @RequestBody TaskRequest request) {
        return service.create(request.toDraft());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Altera uma tarefa", operationId = "updateTask")
    TaskView update(@PathVariable UUID id, @Valid @RequestBody UpdateTaskRequest request) {
        return service.update(id, request.version(), request.task().toDraft());
    }

    @PostMapping("/complete")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(
            summary = "Conclui várias tarefas de uma vez",
            description = "Tudo ou nada: se uma das tarefas for de outra pessoa, nenhuma é concluída.",
            operationId = "completeTasks")
    CompleteTasksResponse complete(@Valid @RequestBody CompleteTasksRequest request) {
        return new CompleteTasksResponse(service.complete(request.ids()));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Reabre uma tarefa concluída", operationId = "reopenTask")
    TaskView reopen(@PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return service.reopen(id, request.version());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Apaga uma tarefa manual", operationId = "deleteTask")
    void delete(@PathVariable UUID id, @RequestParam long version) {
        service.delete(id, version);
    }
}
