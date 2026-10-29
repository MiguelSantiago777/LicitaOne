package br.com.shema.crm.shared.storage;

import java.util.regex.Pattern;

/** Regra única das chaves de arquivo, para disco e S3 aceitarem exatamente as mesmas. */
final class StorageKeys {

    /** Segmentos simples separados por "/": nada de "..", caminho absoluto ou caractere estranho. */
    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*");

    private StorageKeys() {}

    static String require(String key) {
        if (key == null || key.contains("..") || !VALID_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Chave de arquivo inválida: " + key);
        }
        return key;
    }
}
