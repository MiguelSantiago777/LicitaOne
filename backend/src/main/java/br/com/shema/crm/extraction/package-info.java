/**
 * Leitura com IA (ADR 0016). Atas e contratos (Fase 7): o PDF vai para o modelo, e o que ele lê volta como sugestão
 * para o formulário da ata ou do contrato. Pedidos (Fase 8): um PDF, uma foto ou uma mensagem colada é lida contra os
 * itens de um contrato e volta como sugestão para o formulário de pedido. Nada é gravado sem a pessoa conferir.
 *
 * <p>Depende das APIs públicas de {@code pricerecords} ({@code DocumentFiles}) e de {@code contracts}
 * ({@code ContractOrderContext}). As leituras rodam nos consumidores das filas
 * {@code shemacrm.extraction.extraction-requested} e {@code shemacrm.extraction.order-reading-requested}.
 */
@ApplicationModule(displayName = "Leitura com IA")
package br.com.shema.crm.extraction;

import org.springframework.modulith.ApplicationModule;
