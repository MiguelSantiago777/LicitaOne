package br.com.shema.crm.identity.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Chave de assinatura dos tokens.
 *
 * @param privateKey chave privada RSA em PEM (PKCS#8). Vazia = gera uma chave temporária (só dev)
 * @param keyId identificador da chave (header kid), para permitir rotação
 */
@ConfigurationProperties("shema.jwt")
public record JwtProperties(
        String privateKey, @DefaultValue("dev-1") String keyId) {}
