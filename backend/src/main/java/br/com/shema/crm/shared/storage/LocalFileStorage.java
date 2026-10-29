package br.com.shema.crm.shared.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@link FileStorage} em disco, numa pasta configurável ({@code shema.storage.local-dir}). É o padrão. */
@Component
@ConditionalOnProperty(name = "shema.storage.type", havingValue = "local", matchIfMissing = true)
class LocalFileStorage implements FileStorage {

    private final Path root;

    LocalFileStorage(StorageProperties properties) {
        this.root = Path.of(properties.localDir()).toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, byte[] content) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.write(temp, content);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gravar o arquivo " + key, e);
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler o arquivo " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível apagar o arquivo " + key, e);
        }
    }

    private Path resolve(String key) {
        Path path = root.resolve(StorageKeys.require(key)).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Chave de arquivo fora da pasta: " + key);
        }
        return path;
    }
}
