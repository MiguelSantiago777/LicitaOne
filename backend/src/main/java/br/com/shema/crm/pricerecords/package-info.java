/**
 * Atas de registro de preços, caronas (adesões de outros órgãos) e documentos das licitações, com os arquivos no
 * {@code shared.storage.FileStorage} (S3 no compose). Depende das APIs públicas de {@code tenders} (licitação e
 * órgãos), {@code deals} (negociação de origem da carona) e {@code identity} (ADR 0014).
 *
 * <p>{@code CaronaOffers} (Fase 10, ADR 0019) diz o que oferecer de uma ata a um município candidato, para o kit de
 * carona escrito no módulo {@code extraction}.
 */
@ApplicationModule(displayName = "Atas e documentos")
package br.com.shema.crm.pricerecords;

import org.springframework.modulith.ApplicationModule;
