/**
 * Importação assíncrona de planilhas: itens e pedidos de um contrato a partir da aba do órgão na planilha da equipe.
 * Depende da API pública de {@code contracts} ({@code ContractItemsImport}); o processamento roda no consumidor da
 * fila {@code shemacrm.imports.import-requested}.
 */
@ApplicationModule(displayName = "Importação")
package br.com.shema.crm.imports;

import org.springframework.modulith.ApplicationModule;
