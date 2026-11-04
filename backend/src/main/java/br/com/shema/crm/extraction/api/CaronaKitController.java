package br.com.shema.crm.extraction.api;

import br.com.shema.crm.extraction.application.CaronaKitAssistant;
import br.com.shema.crm.extraction.application.CaronaKitAssistant.KitView;
import br.com.shema.crm.extraction.application.CaronaKitAssistant.LetterFile;
import br.com.shema.crm.extraction.application.CaronaKitAssistant.OfferView;
import br.com.shema.crm.extraction.application.LetterDocuments;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Kit de carona (Fase 10, ADR 0019): o que oferecer, os três textos e o .docx de cada um. */
@RestController
@RequestMapping("/api/v1/price-records/{id}")
@Tag(name = "extractions", description = "Leitura de atas, contratos e pedidos com IA")
class CaronaKitController {

    private final CaronaKitAssistant assistant;

    CaronaKitController(CaronaKitAssistant assistant) {
        this.assistant = assistant;
    }

    @Schema(name = "CaronaKitLineRequest")
    record LineRequest(@NotNull UUID itemId, BigDecimal quantity) {}

    /**
     * @param lines quanto pedir de cada item; vazio usa o máximo de cada item
     * @param value ata sem itens: o valor da adesão
     * @param useAi escreve a carta e a justificativa com IA; sem isso, modelos fixos
     */
    @Schema(name = "CaronaKitRequest")
    record KitRequest(
            @NotNull(message = "Escolha o município") UUID agencyId,
            @Valid @Size(max = 500) List<LineRequest> lines,
            BigDecimal value,
            boolean useAi) {}

    /** @param kind APRESENTACAO, SOLICITACAO ou ANUENCIA */
    @Schema(name = "CaronaLetterRequest")
    record LetterRequest(
            @NotNull UUID agencyId,

            @NotNull @Pattern(regexp = "APRESENTACAO|SOLICITACAO|ANUENCIA")
            String kind,

            @NotNull @Size(max = 20_000) String text) {}

    @GetMapping("/carona-offer")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(
            summary = "O que oferecer de uma ata a um município: quanto ele pode pedir de cada item e avisos",
            operationId = "caronaOffer")
    OfferView offer(@PathVariable UUID id, @RequestParam UUID agencyId) {
        return assistant.offer(id, agencyId);
    }

    @PostMapping("/carona-kit")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(
            summary = "Gera o kit de carona (apresentação, modelo de ofício e anuência); não envia nem grava",
            operationId = "writeCaronaKit")
    KitView kit(@PathVariable UUID id, @Valid @RequestBody KitRequest request) {
        return assistant.kit(
                id,
                request.agencyId(),
                request.lines() == null
                        ? List.of()
                        : request.lines().stream()
                                .map(l -> new CaronaKitAssistant.Line(l.itemId(), l.quantity()))
                                .toList(),
                request.value(),
                request.useAi());
    }

    @PostMapping("/carona-kit/docx")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Uma carta do kit de carona em .docx, com o texto revisado", operationId = "caronaLetterDocx")
    @ApiResponse(
            responseCode = "200",
            content = @Content(mediaType = LetterDocuments.DOCX, schema = @Schema(type = "string", format = "binary")))
    ResponseEntity<byte[]> docx(@PathVariable UUID id, @Valid @RequestBody LetterRequest request) {
        LetterFile file = assistant.docx(id, request.agencyId(), request.kind(), request.text());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(LetterDocuments.DOCX))
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
