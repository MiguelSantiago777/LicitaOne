package br.com.shema.crm.proposals.domain;

/** Proposta de fornecimento de produtos ou de prestação de serviços: muda o título do documento. */
public enum ProposalKind {
    PRODUTO("Fornecimento de produtos"),
    SERVICO("Prestação de serviços");

    private final String title;

    ProposalKind(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
