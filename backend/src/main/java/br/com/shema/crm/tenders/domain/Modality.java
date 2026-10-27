package br.com.shema.crm.tenders.domain;

public enum Modality {
    PREGAO_ELETRONICO("Pregão eletrônico", "PE"),
    PREGAO_PRESENCIAL("Pregão presencial", "PP"),
    DISPENSA("Dispensa", "Dispensa"),
    CONCORRENCIA("Concorrência", "Concorrência"),
    OUTRA("Outra", "Processo");

    private final String label;
    private final String prefix;

    Modality(String label, String prefix) {
        this.label = label;
        this.prefix = prefix;
    }

    public String label() {
        return label;
    }

    public String prefix() {
        return prefix;
    }
}
