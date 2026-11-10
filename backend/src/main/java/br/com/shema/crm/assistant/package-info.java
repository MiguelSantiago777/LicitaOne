/**
 * "Pergunte ao LicitaOne" (Fase 11, ADR 0020): perguntas em português sobre a carteira, respondidas pelo modelo com
 * consultas fixas de leitura, executadas como o usuário logado. O assistente só lê: não cria, altera nem apaga nada.
 * A lista clicável da resposta só leva itens que as consultas devolveram nesta pergunta.
 *
 * <p>Depende só das APIs públicas: {@code tenders} ({@code TenderSearch}, {@code AgencyDirectory},
 * {@code TenderContacts}), {@code contracts} ({@code ContractOrderContext}), {@code pricerecords}
 * ({@code PriceRecordSearch}), {@code tasks} ({@code TaskSearch}), {@code deals} ({@code DealSearch}) e
 * {@code identity} ({@code UserDirectory}). As perguntas não são guardadas; só a contagem diária por pessoa.
 */
@ApplicationModule(displayName = "Assistente")
package br.com.shema.crm.assistant;

import org.springframework.modulith.ApplicationModule;
