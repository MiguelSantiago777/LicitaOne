package br.com.shema.crm.tenders.domain;

public enum Sphere {
    MUNICIPAL("Municipal"),
    ESTADUAL("Estadual"),
    FEDERAL("Federal"),
    OUTRA("Outra");

    private final String label;

    Sphere(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
