package br.com.shema.crm.tasks.domain;

/** Situação mostrada na lista. "Atrasada" é calculada pela hora atual, nunca gravada. */
public enum TaskStatus {
    ABERTA,
    ATRASADA,
    CONCLUIDA
}
