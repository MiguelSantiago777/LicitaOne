package br.com.shema.crm.shared.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** {@link FileStorage} em disco, numa pasta configurável ({@code shema.storage.local-dir}). */
@Component
class LocalFileStorage implements FileStorage {

    /** Segmentos simples separados por "/": nada de "..", caminho absoluto ou caractere estranho. */
    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*");

    private final Path root;

    LocalFileStorage(@Value("${shema.storage.local-dir}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
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
        if (key == null || key.contains("..") || !VALID_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Chave de arquivo inválida: " + key);
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Chave de arquivo fora da pasta: " + key);
        }
        return path;
    }
}
