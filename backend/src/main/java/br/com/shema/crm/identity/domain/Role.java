package br.com.shema.crm.identity.domain;

/** Perfis de acesso. O rótulo é o nome que aparece na interface. */
public enum Role {
    ADMIN("Administrador"),
    SALES("Comercial"),
    VIEWER("Leitura");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
