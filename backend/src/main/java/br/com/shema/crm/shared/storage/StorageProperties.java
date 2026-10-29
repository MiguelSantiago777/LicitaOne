package br.com.shema.crm.shared.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Onde ficam os arquivos ({@code shema.storage.*}).
 *
 * @param type {@code local} (pasta em disco, padrão) ou {@code s3}
 */
@ConfigurationProperties("shema.storage")
public record StorageProperties(
        @DefaultValue("local") String type,
        String localDir,
        @DefaultValue S3 s3) {

    /**
     * @param endpoint vazio usa o endpoint da AWS para a região
     * @param pathStyle SeaweedFS e MinIO precisam de {@code http://host/bucket/chave}
     */
    public record S3(
            String endpoint,
            @DefaultValue("us-east-1") String region,
            @DefaultValue("shemacrm-files") String bucket,
            String accessKey,
            String secretKey,
            @DefaultValue("true") boolean pathStyle) {}
}
