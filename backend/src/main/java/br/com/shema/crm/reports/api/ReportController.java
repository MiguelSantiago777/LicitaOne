package br.com.shema.crm.reports.api;

import br.com.shema.crm.reports.application.ReportMetric;
import br.com.shema.crm.reports.application.ReportService;
import br.com.shema.crm.reports.application.ReportView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "reports", description = "Relatórios para o balanço")
class ReportController {

    private final ReportService service;

    ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Um número do balanço num ano: mês a mês, anos, órgãos, municípios, UFs e tipos",
            description =
                    "year padrão é o atual. state (UF) e sphere (MUNICIPAL, ESTADUAL, FEDERAL, OUTRA) filtram pelo"
                            + " órgão.",
            operationId = "getReport")
    ReportView report(
            @RequestParam ReportMetric metric,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String sphere) {
        return service.report(metric, year, state, sphere);
    }

    @GetMapping("/pdf")
    @Operation(summary = "O mesmo relatório em PDF, com os gráficos", operationId = "downloadReportPdf")
    @ApiResponse(
            responseCode = "200",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PDF_VALUE,
                            schema = @Schema(type = "string", format = "binary")))
    ResponseEntity<byte[]> pdf(
            @RequestParam ReportMetric metric,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String sphere) {
        ReportService.Document file = service.pdf(metric, year, state, sphere);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
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
