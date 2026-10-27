package br.com.shema.crm.identity;

import java.util.UUID;

/** Dados públicos de um usuário, para outros módulos mostrarem nome e avatar. */
public record UserSummary(UUID id, String name, String initials, boolean active) {}
