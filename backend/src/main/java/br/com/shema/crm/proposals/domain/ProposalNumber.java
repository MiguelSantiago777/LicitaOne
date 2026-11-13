package br.com.shema.crm.proposals.domain;

/** Número da proposta: sequencial dentro do ano, "2026/0001". Dado na criação e nunca mais muda. */
public record ProposalNumber(int year, int sequence) {

    public ProposalNumber {
        if (year < 2000 || sequence < 1) {
            throw new IllegalArgumentException("Número de proposta inválido: " + year + "/" + sequence);
        }
    }

    public String text() {
        return "%d/%04d".formatted(year, sequence);
    }

    /** Para o nome do arquivo, sem a barra: "Proposta-2026-0001.pdf". */
    public String fileName() {
        return "Proposta-%d-%04d.pdf".formatted(year, sequence);
    }
}
