package br.com.shema.crm.identity;

/** Quem assina uma proposta de preços: nome, cargo e CPF (só dígitos). Cargo e CPF podem faltar. */
public record SignerProfile(String name, String jobTitle, String cpf) {}
