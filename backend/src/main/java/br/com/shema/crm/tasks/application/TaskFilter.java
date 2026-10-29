package br.com.shema.crm.tasks.application;

import java.util.UUID;

/**
 * Filtros da lista de tarefas. Tudo opcional.
 *
 * @param ownerId só as tarefas deste responsável ("Minhas tarefas" manda o usuário atual)
 */
public record TaskFilter(Situation situation, String q, UUID ownerId, UUID dealId, UUID tenderId) {

    /** Abertas inclui as atrasadas, como no print da lista. */
    public enum Situation {
        ABERTAS,
        ATRASADAS,
        CONCLUIDAS,
        TODAS
    }
}
