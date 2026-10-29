package br.com.shema.crm.notifications.api;

import br.com.shema.crm.identity.UserDirectory;
import br.com.shema.crm.identity.UserSummary;
import br.com.shema.crm.notifications.application.AlertProperties;
import br.com.shema.crm.notifications.application.AlertRunner;
import br.com.shema.crm.notifications.application.AlertScan;
import br.com.shema.crm.notifications.domain.AlertRule;
import br.com.shema.crm.notifications.domain.AlertSettings;
import br.com.shema.crm.notifications.infrastructure.AlertLog;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import br.com.shema.crm.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alerts")
@Tag(name = "alerts", description = "Alertas, tarefas automáticas e avisos por e-mail")
class AlertController {

    record AlertOwnerResponse(UUID id, String name, String initials) {}

    /**
     * @param taskId tarefa criada pelo alerta; nulo enquanto a fila não processou
     * @param notifiedTo para quem foi o aviso interno; nulo se ainda não foi (ou se o e-mail está desligado)
     */
    record AlertResponse(
            UUID id,
            AlertRule rule,
            String ruleLabel,
            UUID tenderId,
            String agencyName,
            String title,
            LocalDate dueOn,
            AlertOwnerResponse owner,
            Instant raisedAt,
            UUID taskId,
            Instant taskOpenedAt,
            String notifiedTo,
            Instant notifiedAt) {}

    record AlertRuleResponse(AlertRule rule, String label, String description) {}

    /**
     * @param schedule quando o job roda, para mostrar na tela
     * @param lastRunAt última vez que o job rodou (agendado ou "verificar agora")
     */
    record AlertSettingsResponse(
            int contractWithoutOrderDays,
            List<Integer> priceRecordExpiringDays,
            int tenderWithoutContractDays,
            String schedule,
            boolean mailEnabled,
            Instant lastRunAt,
            List<AlertRuleResponse> rules) {}

    record RunAlertsResponse(LocalDate day, Instant at, int raised, Map<AlertRule, Integer> byRule) {}

    private final AlertLog alertLog;
    private final AlertRunner runner;
    private final AlertProperties properties;
    private final UserDirectory users;

    AlertController(AlertLog alertLog, AlertRunner runner, AlertProperties properties, UserDirectory users) {
        this.alertLog = alertLog;
        this.runner = runner;
        this.properties = properties;
        this.users = users;
    }

    @GetMapping
    @Operation(summary = "Alertas gerados, dos mais recentes", operationId = "searchAlerts")
    PageResponse<AlertResponse> search(
            @RequestParam(required = false) AlertRule rule,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, 100);
        List<AlertLog.Row> rows = alertLog.page(rule, safePage, safeSize);
        long total = alertLog.count(rule);
        Map<UUID, UserSummary> owners = users.findAll(rows.stream()
                .map(AlertLog.Row::ownerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        List<AlertResponse> items = rows.stream()
                .map(r -> {
                    UserSummary owner = r.ownerId() == null ? null : owners.get(r.ownerId());
                    return new AlertResponse(
                            r.id(),
                            r.rule(),
                            r.rule().label(),
                            r.tenderId(),
                            r.agencyName(),
                            r.title(),
                            r.dueOn(),
                            owner == null ? null : new AlertOwnerResponse(owner.id(), owner.name(), owner.initials()),
                            r.raisedAt(),
                            r.taskId(),
                            r.taskOpenedAt(),
                            r.notifiedTo(),
                            r.notifiedAt());
                })
                .toList();
        return new PageResponse<>(items, safePage, safeSize, total, (int) ((total + safeSize - 1) / safeSize));
    }

    @GetMapping("/settings")
    @Operation(summary = "Prazos das regras, agenda do job e última verificação", operationId = "alertSettings")
    AlertSettingsResponse settings() {
        AlertSettings s = properties.settings();
        String bands = s.priceRecordExpiringDays().stream().map(String::valueOf).collect(Collectors.joining(", "));
        return new AlertSettingsResponse(
                s.contractWithoutOrderDays(),
                s.priceRecordExpiringDays(),
                s.tenderWithoutContractDays(),
                schedule(properties.cron()),
                properties.mailEnabled(),
                alertLog.lastRun(AlertRunner.LOCK).orElse(null),
                List.of(
                        new AlertRuleResponse(
                                AlertRule.CONTRATO_SEM_PEDIDO,
                                AlertRule.CONTRATO_SEM_PEDIDO.label(),
                                "Contrato assinado há " + s.contractWithoutOrderDays()
                                        + " dias ou mais sem nenhum pedido: tarefa com rascunho de e-mail ao órgão."),
                        new AlertRuleResponse(
                                AlertRule.ATA_VENCENDO,
                                AlertRule.ATA_VENCENDO.label(),
                                "Ata a " + bands + " dias do vencimento: uma tarefa por faixa."),
                        new AlertRuleResponse(
                                AlertRule.LICITACAO_SEM_CONTRATO,
                                AlertRule.LICITACAO_SEM_CONTRATO.label(),
                                "Licitação vencida há " + s.tenderWithoutContractDays()
                                        + " dias ou mais sem contrato assinado: tarefa já atrasada.")));
    }

    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Verifica os alertas agora, sem esperar o horário do job", operationId = "runAlerts")
    RunAlertsResponse run() {
        AlertScan.Result result = runner.runNow()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.CONFLICT, "Os alertas já estão sendo verificados. Tente de novo em instantes."));
        return new RunAlertsResponse(result.day(), result.at(), result.total(), result.raised());
    }

    /** A agenda padrão em palavras; outra expressão aparece como está. */
    private static String schedule(String cron) {
        if (cron == null || cron.equals("-")) {
            return "Desligado";
        }
        String[] parts = cron.trim().split("\\s+");
        boolean daily = parts.length == 6
                && parts[3].equals("*")
                && parts[4].equals("*")
                && parts[5].equals("*")
                && parts[0].matches("\\d+")
                && parts[1].matches("\\d+")
                && parts[2].matches("\\d+");
        if (daily) {
            int hour = Integer.parseInt(parts[2]);
            int minute = Integer.parseInt(parts[1]);
            return "Todo dia às " + hour + "h" + (minute == 0 ? "" : String.format("%02d", minute))
                    + " (horário de Brasília)";
        }
        return cron;
    }
}
