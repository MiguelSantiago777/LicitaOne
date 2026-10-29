/**
 * Tarefas manuais e, a partir da Fase 5, geradas por alertas. Lê licitações pela API pública de {@code tenders} e mostra
 * a negociação ligada por {@link br.com.shema.crm.tasks.TaskSubjects}, que o módulo {@code deals} implementa: assim
 * {@code tasks} não depende de {@code deals} (ADR 0013).
 */
@ApplicationModule(displayName = "Tarefas")
package br.com.shema.crm.tasks;

import org.springframework.modulith.ApplicationModule;
