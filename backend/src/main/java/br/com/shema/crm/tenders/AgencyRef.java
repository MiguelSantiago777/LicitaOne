package br.com.shema.crm.tenders;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Dados públicos de um órgão, para outros módulos mostrarem e validarem sem conhecer a entidade. */
@Schema(name = "AgencySummaryResponse")
public record AgencyRef(UUID id, String name, String city, String state) {}
