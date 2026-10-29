package br.com.shema.crm.notifications.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Um alerta pronto para sair: o que a tarefa vai dizer, o rascunho ao órgão (se houver) e o aviso para a equipe.
 *
 * @param subjectId o contrato, a ata ou a licitação que gerou o alerta
 * @param dueOn vencimento da tarefa
 * @param draft rascunho de e-mail ao órgão, ou nulo
 */
public record Alert(
        String key,
        AlertRule rule,
        UUID tenderId,
        UUID subjectId,
        String agencyName,
        String title,
        String notes,
        LocalDate dueOn,
        UUID ownerId,
        Draft draft,
        Notice notice) {

    /** Rascunho ao órgão: o sistema nunca envia (seção 5.4). */
    public record Draft(String to, String subject, String body) {}

    /** E-mail interno para a equipe (ADR 0006). */
    public record Notice(String subject, String body) {}
}
