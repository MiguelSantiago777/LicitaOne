package br.com.shema.crm.pricerecords.domain;

/** Tipos de documento da seção 5.3 da especificação. */
public enum DocumentKind {
    ATA("Ata"),
    CONTRATO("Contrato"),
    EMPENHO("Empenho"),
    ATESTADO("Atestado"),
    EDITAL("Edital"),
    OUTROS("Outros");

    private final String label;

    DocumentKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
