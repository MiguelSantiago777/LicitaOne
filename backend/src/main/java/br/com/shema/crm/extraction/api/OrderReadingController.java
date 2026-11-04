package br.com.shema.crm.extraction.api;

import br.com.shema.crm.extraction.application.OrderReadingService;
import br.com.shema.crm.extraction.application.OrderReadingService.OrderReadingView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "extractions", description = "Leitura de atas, contratos e pedidos com IA")
class OrderReadingController {

    private final OrderReadingService service;

    OrderReadingController(OrderReadingService service) {
        this.service = service;
    }

    /**
     * O pedido a ler: um documento já enviado em Documentos (PDF ou foto) ou o texto de um e-mail ou mensagem. Informe
     * um dos dois.
     */
    record OrderReadingRequest(
            UUID documentId,
            @Size(max = OrderReadingService.MAX_TEXT + 1000) String text) {}

    @PostMapping("/contracts/{contractId}/order-readings")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(
            summary = "Pede para a IA ler um pedido (documento ou mensagem) contra os itens do contrato",
            operationId = "requestOrderReading")
    OrderReadingView request(@PathVariable UUID contractId, @Valid @RequestBody OrderReadingRequest request) {
        return request.documentId() != null
                ? service.fromDocument(contractId, request.documentId())
                : service.fromText(contractId, request.text());
    }

    @GetMapping("/order-readings/{readingId}")
    @Operation(summary = "Situação e resultado de uma leitura de pedido", operationId = "getOrderReading")
    OrderReadingView get(@PathVariable UUID readingId) {
        return service.get(readingId);
    }
}
