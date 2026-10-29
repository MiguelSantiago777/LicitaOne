package br.com.shema.crm.tenders;

import java.util.UUID;

/** Dados públicos de um órgão, para outros módulos mostrarem e validarem sem conhecer a entidade. */
public record AgencyRef(UUID id, String name, String city, String state) {}
