package br.com.shema.crm.identity.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Primeiro administrador, criado na subida quando ainda não existe nenhum usuário. */
@ConfigurationProperties("shema.bootstrap-admin")
public record BootstrapAdminProperties(
        String email,
        String password,
        @DefaultValue("Administrador") String name) {}
