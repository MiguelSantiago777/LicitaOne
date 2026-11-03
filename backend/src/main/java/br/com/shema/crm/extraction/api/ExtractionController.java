package br.com.shema.crm.extraction.api;

import br.com.shema.crm.extraction.application.ExtractionService;
import br.com.shema.crm.extraction.application.ExtractionService.ExtractionView;
import br.com.shema.crm.extraction.application.ExtractionService.Settings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "extractions", description = "Leitura de atas e contratos com IA")
class ExtractionController {

    private final ExtractionService service;

    ExtractionController(ExtractionService service) {
        this.service = service;
    }

    @GetMapping("/extractions/settings")
    @Operation(summary = "Se a leitura com IA está ligada neste ambiente", operationId = "getExtractionSettings")
    Settings settings() {
        return service.settings();
    }

    @PostMapping("/documents/{documentId}/extractions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(
            summary = "Pede para a IA ler a versão atual do documento (processada em segundo plano)",
            operationId = "requestExtraction")
    ExtractionView request(@PathVariable UUID documentId) {
        return service.request(documentId);
    }

    @GetMapping("/documents/{documentId}/extractions/latest")
    @Operation(
            summary = "Leitura mais recente do documento (204 se nunca foi lido)",
            operationId = "getLatestExtraction")
    ResponseEntity<ExtractionView> latest(@PathVariable UUID documentId) {
        return service.latest(documentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/extractions/{extractionId}")
    @Operation(summary = "Situação e resultado de uma leitura", operationId = "getExtraction")
    ExtractionView get(@PathVariable UUID extractionId) {
        return service.get(extractionId);
    }
}
