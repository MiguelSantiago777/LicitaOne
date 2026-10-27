package br.com.shema.crm.tenders.domain;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Órgão: o ente público que compra (prefeitura, batalhão, secretaria, instituto...). */
@Entity
@Table(name = "agencies")
public class Agency extends AuditableEntity {

    private static final Set<String> UFS = Set.of(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI",
            "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Sphere sphere;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(nullable = false, length = 2)
    private String state;

    @Column(length = 14)
    private String cnpj;

    @Column(name = "default_uasg", length = 6)
    private String defaultUasg;

    @Column(length = 2000)
    private String notes;

    protected Agency() {}

    public Agency(String name, Sphere sphere, String city, String state, String cnpj, Uasg defaultUasg, String notes) {
        this.id = UUID.randomUUID();
        update(name, sphere, city, state, cnpj, defaultUasg, notes);
    }

    public void update(
            String name, Sphere sphere, String city, String state, String cnpj, Uasg defaultUasg, String notes) {
        this.name = requireText(name, "o nome do órgão");
        this.sphere = Objects.requireNonNull(sphere, "sphere");
        this.city = requireText(city, "o município");
        String uf = requireText(state, "a UF").toUpperCase(Locale.ROOT);
        if (!UFS.contains(uf)) {
            throw new DomainRuleViolation("STATE_INVALID", "UF inválida: " + state);
        }
        this.state = uf;
        this.cnpj = normalizeCnpj(cnpj);
        this.defaultUasg = defaultUasg == null ? null : defaultUasg.value();
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    private static String normalizeCnpj(String cnpj) {
        if (cnpj == null || cnpj.isBlank()) {
            return null;
        }
        String digits = cnpj.replaceAll("\\D", "");
        if (digits.length() != 14) {
            throw new DomainRuleViolation("CNPJ_INVALID", "O CNPJ tem 14 dígitos.");
        }
        return digits;
    }

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleViolation("REQUIRED", "Informe " + what + ".");
        }
        return value.trim();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Sphere getSphere() {
        return sphere;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getCnpj() {
        return cnpj;
    }

    public Uasg getDefaultUasg() {
        return Uasg.ofNullable(defaultUasg);
    }

    public String getNotes() {
        return notes;
    }
}
