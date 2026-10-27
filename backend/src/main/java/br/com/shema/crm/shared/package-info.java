/**
 * Infraestrutura comum a todos os módulos: erros da API (RFC 7807), correlationId, auditoria e relógio.
 * Módulo aberto: qualquer módulo pode depender dele, e ele não depende de nenhum.
 */
@ApplicationModule(displayName = "Compartilhado", type = ApplicationModule.Type.OPEN)
package br.com.shema.crm.shared;

import org.springframework.modulith.ApplicationModule;
