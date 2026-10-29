package br.com.shema.crm.notifications.application;

import br.com.shema.crm.notifications.domain.AlertSettings;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Alertas ({@code shema.alerts}). Os prazos vêm da especificação (5.4), menos o da licitação vencida sem contrato, que
 * a especificação deixou como "X dias" e precisa de validação do cliente (ADR 0015).
 *
 * @param cron quando o job roda (padrão: todo dia às 7h, horário de Brasília); "-" desliga
 * @param appUrl endereço do LicitaOne, para o link nos e-mails internos
 * @param mailFrom remetente dos avisos internos
 * @param mailEnabled liga o aviso interno por e-mail (ADR 0006); a tarefa sai de qualquer jeito
 */
@ConfigurationProperties("shema.alerts")
public record AlertProperties(
        String cron,
        int contractWithoutOrderDays,
        List<Integer> priceRecordExpiringDays,
        int tenderWithoutContractDays,
        String appUrl,
        String mailFrom,
        boolean mailEnabled) {

    public AlertSettings settings() {
        return new AlertSettings(contractWithoutOrderDays, priceRecordExpiringDays, tenderWithoutContractDays);
    }
}
