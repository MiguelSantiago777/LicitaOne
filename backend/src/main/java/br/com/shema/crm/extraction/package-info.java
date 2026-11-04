/**
 * Leitura com IA (ADR 0016). Atas e contratos (Fase 7): o PDF vai para o modelo, e o que ele lê volta como sugestão
 * para o formulário da ata ou do contrato. Pedidos (Fase 8): um PDF, uma foto ou uma mensagem colada é lida contra os
 * itens de um contrato e volta como sugestão para o formulário de pedido. Nada é gravado sem a pessoa conferir.
 *
 * Follow-up (Fase 9): entende a resposta colada de um órgão e escreve o follow-up com o contexto do contrato, na
 * hora, sem fila.
 *
 * Kit de carona (Fase 10): carta de apresentação e justificativa escritas na hora pela IA, modelo de ofício e
 * anuência em modelos fixos, e o .docx de cada um.
 *
 * <p>Depende das APIs públicas de {@code pricerecords} ({@code DocumentFiles}, {@code CaronaOffers}), de {@code contracts}
 * ({@code ContractOrderContext}) e de {@code tenders} ({@code TenderLifecycle}, {@code TenderContacts},
 * {@code AgencyDirectory}). As leituras de documento rodam nos consumidores das filas
 * {@code shemacrm.extraction.extraction-requested} e {@code shemacrm.extraction.order-reading-requested}.
 */
@ApplicationModule(displayName = "Leitura com IA")
package br.com.shema.crm.extraction;

import org.springframework.modulith.ApplicationModule;
