/**
 * Contratos (e empenhos, e dispensas), itens, pedidos e saldo por quantidade e por valor. Depende da API pública de
 * {@code tenders} ({@code TenderLifecycle}): avisa a licitação de assinaturas, pedidos e encerramentos (ADR 0012); e
 * da de {@code pricerecords} ({@code DocumentFiles}), para conferir o documento de origem de um pedido.
 */
@ApplicationModule(displayName = "Contratos")
package br.com.shema.crm.contracts;

import org.springframework.modulith.ApplicationModule;
