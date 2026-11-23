package br.com.shema.crm.catalog.domain;

import java.util.List;
import java.util.UUID;

/** O conteúdo editável do fornecedor, tudo de uma vez: a tela salva sozinha a cada alteração. */
public record SupplierDraft(
        String legalName,
        String tradeName,
        String cnpj,
        String city,
        String state,
        String website,
        String notes,
        List<UUID> nicheIds,
        List<Contact> contacts) {

    public record Contact(String name, String phone, String email) {}
}
