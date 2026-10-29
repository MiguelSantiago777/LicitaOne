package br.com.shema.crm.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** O mesmo SeaweedFS do compose (ADR 0007): cria o bucket, grava, lê, confere e apaga. */
@Testcontainers
class S3FileStorageTest {

    @Container
    static final GenericContainer<?> seaweed = new GenericContainer<>("chrislusf/seaweedfs:4.51")
            .withCommand("server", "-dir=/data", "-s3", "-s3.port=8333")
            .withExposedPorts(8333)
            .waitingFor(Wait.forListeningPorts(8333).withStartupTimeout(Duration.ofMinutes(2)));

    static S3FileStorage storage;

    @BeforeAll
    static void connect() {
        String endpoint = "http://" + seaweed.getHost() + ":" + seaweed.getMappedPort(8333);
        // Sem arquivo de identidades, o SeaweedFS aceita qualquer credencial
        storage = new S3FileStorage(new StorageProperties(
                "s3", null, new StorageProperties.S3(endpoint, "us-east-1", "teste", "chave", "segredo", true)));
    }

    @AfterAll
    static void close() {
        storage.close();
    }

    @Test
    void storesReadsAndDeletes() {
        String key = "documentos/abc/v1.pdf";
        assertThat(storage.exists(key)).isFalse();

        storage.put(key, "%PDF-1.7 ata".getBytes(StandardCharsets.UTF_8));
        assertThat(storage.exists(key)).isTrue();
        assertThat(new String(storage.get(key), StandardCharsets.UTF_8)).isEqualTo("%PDF-1.7 ata");

        storage.put(key, "%PDF-1.7 ata v2".getBytes(StandardCharsets.UTF_8));
        assertThat(new String(storage.get(key), StandardCharsets.UTF_8)).isEqualTo("%PDF-1.7 ata v2");

        storage.delete(key);
        assertThat(storage.exists(key)).isFalse();
    }

    @Test
    void rejectsKeysOutsideTheRule() {
        assertThatThrownBy(() -> storage.put("../fora", new byte[] {1})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.get("/absoluto")).isInstanceOf(IllegalArgumentException.class);
    }
}
