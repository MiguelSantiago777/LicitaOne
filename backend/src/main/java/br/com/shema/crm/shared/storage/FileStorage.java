package br.com.shema.crm.shared.storage;

/**
 * Onde ficam os binários (planilhas importadas, relatórios e, na Fase 3, documentos). O banco guarda só os metadados
 * e a chave. A implementação é escolhida por configuração: disco local no MVP, S3 (ADR 0007) como alvo.
 */
public interface FileStorage {

    /** Grava o conteúdo na chave (sobrescreve se já existir). Chaves usam "/" como separador. */
    void put(String key, byte[] content);

    /** Conteúdo da chave; erro se não existir. */
    byte[] get(String key);

    boolean exists(String key);

    void delete(String key);
}
