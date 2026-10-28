package br.com.shema.crm.contracts.domain;

/** Instrumento que formaliza a compra (ADR 0005): o mesmo órgão pode emitir vários sobre uma licitação. */
public enum ContractKind {
    CONTRATO("Contrato"),
    EMPENHO("Empenho"),
    DISPENSA("Dispensa");

    private final String label;

    ContractKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
