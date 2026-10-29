/**
 * Atas de registro de preços, caronas (adesões de outros órgãos) e documentos das licitações, com os arquivos no
 * {@code shared.storage.FileStorage} (S3 no compose). Depende das APIs públicas de {@code tenders} (licitação e
 * órgãos), {@code deals} (negociação de origem da carona) e {@code identity} (ADR 0014).
 */
@ApplicationModule(displayName = "Atas e documentos")
package br.com.shema.crm.pricerecords;

import org.springframework.modulith.ApplicationModule;
