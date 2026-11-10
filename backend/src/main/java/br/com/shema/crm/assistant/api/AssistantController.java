package br.com.shema.crm.assistant.api;

import br.com.shema.crm.assistant.application.AssistantService;
import br.com.shema.crm.assistant.application.AssistantService.PastTurn;
import br.com.shema.crm.assistant.application.AssistantService.QuestionView;
import br.com.shema.crm.assistant.application.AssistantService.SettingsView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** "Pergunte ao LicitaOne" (Fase 11, ADR 0020). Todos os perfis perguntam; o assistente só lê. */
@RestController
@RequestMapping("/api/v1/assistant")
@Tag(name = "assistant", description = "Perguntas sobre a carteira, respondidas com IA")
class AssistantController {

    private final AssistantService service;

    AssistantController(AssistantService service) {
        this.service = service;
    }

    @Schema(name = "AssistantPastTurnRequest")
    record PastTurnRequest(
            @Size(max = 2000) String question,
            @Size(max = 4000) String answer) {}

    @Schema(name = "AssistantQuestionRequest")
    record QuestionRequest(
            @NotNull(message = "Escreva a pergunta") @Size(max = AssistantService.MAX_QUESTION)
            String question,

            @Valid @Size(max = 20) List<PastTurnRequest> history) {}

    @GetMapping
    @Operation(summary = "Se o assistente está ligado, o limite do dia e exemplos", operationId = "assistantSettings")
    SettingsView settings() {
        return service.settings();
    }

    @PostMapping("/questions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Faz uma pergunta; a resposta sai em segundo plano (consulte pelo id). Nada é gravado.",
            operationId = "askAssistant")
    QuestionView ask(@Valid @RequestBody QuestionRequest request) {
        return service.ask(
                request.question(),
                request.history() == null
                        ? List.of()
                        : request.history().stream()
                                .map(t -> new PastTurn(t.question(), t.answer()))
                                .toList());
    }

    @GetMapping("/questions/{id}")
    @Operation(summary = "Andamento e resposta de uma pergunta", operationId = "assistantQuestion")
    QuestionView question(@PathVariable UUID id) {
        return service.get(id);
    }
}
