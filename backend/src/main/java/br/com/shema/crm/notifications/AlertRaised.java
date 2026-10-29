package br.com.shema.crm.notifications;

import br.com.shema.crm.shared.messaging.EventMetadata;
import br.com.shema.crm.shared.messaging.IntegrationEvent;
import br.com.shema.crm.shared.messaging.TracedEvent;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Um alerta novo (seção 5.4). O job só publica este evento; os consumidores criam a tarefa e mandam o aviso interno.
 * Carrega tudo o que eles precisam, para não consultarem outros módulos.
 *
 * @param rule CONTRATO_SEM_PEDIDO, ATA_VENCENDO ou LICITACAO_SEM_CONTRATO
 * @param draft rascunho de e-mail ao órgão, ou nulo
 */
@IntegrationEvent(exchange = AlertRaised.EXCHANGE, type = AlertRaised.TYPE)
public record AlertRaised(
        EventMetadata metadata,
        UUID alertId,
        String alertKey,
        String rule,
        UUID tenderId,
        String title,
        String notes,
        LocalDate dueOn,
        UUID ownerId,
        Draft draft,
        String noticeSubject,
        String noticeBody)
        implements TracedEvent {

    public static final String EXCHANGE = "shemacrm.alerts";
    public static final String TYPE = "alert.raised";

    public record Draft(String to, String subject, String body) {}
}
