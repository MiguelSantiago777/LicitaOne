/**
 * Leitura de atas e contratos com IA (Fase 7, ADR 0016): o PDF de um documento vai para o modelo, e o que ele lê
 * volta como sugestão para o formulário da ata ou do contrato. Nada é gravado sem a pessoa conferir. Depende da API
 * pública de {@code pricerecords} ({@code DocumentFiles}); a leitura roda no consumidor da fila
 * {@code shemacrm.extraction.extraction-requested}.
 */
@ApplicationModule(displayName = "Leitura com IA")
package br.com.shema.crm.extraction;

import org.springframework.modulith.ApplicationModule;
