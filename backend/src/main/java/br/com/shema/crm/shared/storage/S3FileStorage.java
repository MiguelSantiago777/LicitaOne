package br.com.shema.crm.shared.storage;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * {@link FileStorage} em qualquer serviço compatível com S3 (ADR 0007): SeaweedFS no compose, AWS S3, R2 ou Garage
 * em produção. Só a API S3, nada específico do provedor. O bucket é criado na subida se ainda não existir.
 */
@Component
@ConditionalOnProperty(name = "shema.storage.type", havingValue = "s3")
class S3FileStorage implements FileStorage, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(S3FileStorage.class);

    private final S3Client client;
    private final String bucket;

    S3FileStorage(StorageProperties properties) {
        StorageProperties.S3 s3 = properties.s3();
        this.bucket = s3.bucket();
        var builder = S3Client.builder()
                .httpClient(UrlConnectionHttpClient.create())
                .region(Region.of(s3.region()))
                .forcePathStyle(s3.pathStyle())
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create(s3.accessKey(), s3.secretKey())));
        if (s3.endpoint() != null && !s3.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(s3.endpoint()));
        }
        this.client = builder.build();
        ensureBucket();
    }

    private void ensureBucket() {
        try {
            client.headBucket(b -> b.bucket(bucket));
        } catch (NoSuchBucketException e) {
            client.createBucket(b -> b.bucket(bucket));
            log.info("Bucket {} criado", bucket);
        }
    }

    @Override
    public void put(String key, byte[] content) {
        client.putObject(p -> p.bucket(bucket).key(StorageKeys.require(key)), RequestBody.fromBytes(content));
    }

    @Override
    public byte[] get(String key) {
        return client.getObjectAsBytes(g -> g.bucket(bucket).key(StorageKeys.require(key)))
                .asByteArray();
    }

    @Override
    public boolean exists(String key) {
        try {
            client.headObject(h -> h.bucket(bucket).key(StorageKeys.require(key)));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Override
    public void delete(String key) {
        client.deleteObject(d -> d.bucket(bucket).key(StorageKeys.require(key)));
    }

    @Override
    public void close() {
        client.close();
    }
}
