/**
 * Alertas da seção 5.4 (ADR 0015). Um job diário com ShedLock busca os candidatos pelas APIs públicas de
 * {@code contracts}, {@code tenders} e {@code pricerecords}, e publica {@code alert.raised} pelo outbox. Os
 * consumidores criam a tarefa (API pública de {@code tasks}) e mandam o aviso interno por e-mail ({@code identity}
 * informa os destinatários).
 */
@ApplicationModule(displayName = "Alertas")
package br.com.shema.crm.notifications;

import org.springframework.modulith.ApplicationModule;
