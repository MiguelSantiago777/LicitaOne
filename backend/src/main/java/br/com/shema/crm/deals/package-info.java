/**
 * Negociações (funil de vendas): etapas configuráveis e negociações ligadas a um órgão e, opcionalmente, à licitação de
 * origem (ex.: prospectar carona de uma ata). Depende das APIs públicas de {@code tenders} (órgãos e licitações),
 * {@code identity} e {@code tasks}; implementa {@code tasks.TaskSubjects} (ADR 0013).
 */
@ApplicationModule(displayName = "Negociações")
package br.com.shema.crm.deals;

import org.springframework.modulith.ApplicationModule;
