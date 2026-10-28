package br.com.shema.crm.tenders.domain;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Pessoa de contato num órgão (para o follow-up de pedidos). Dado pessoal (LGPD): só o mínimo para falar com a
 * pessoa, nada de documento ou endereço residencial.
 */
@Entity
@Table(name = "agency_contacts")
public class AgencyContact {

    public static final int MAX_PER_AGENCY = 20;

    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    @Id
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 80)
    private String role;

    @Column(length = 160)
    private String email;

    /** Só dígitos, com DDD (ex.: 21987654321). */
    @Column(length = 13)
    private String phone;

    @Column(length = 500)
    private String notes;

    protected AgencyContact() {}

    public AgencyContact(
            UUID agencyId, int position, String name, String role, String email, String phone, String notes) {
        this.id = UUID.randomUUID();
        this.agencyId = agencyId;
        this.position = position;
        if (name == null || name.isBlank()) {
            throw new DomainRuleViolation("REQUIRED", "Informe o nome do contato.");
        }
        this.name = name.trim();
        this.role = blankToNull(role);
        this.email = email(email);
        this.phone = phone(phone);
        this.notes = blankToNull(notes);
    }

    private static String email(String value) {
        String email = blankToNull(value);
        if (email == null) {
            return null;
        }
        if (!EMAIL.matcher(email).matches()) {
            throw new DomainRuleViolation("EMAIL_INVALID", "E-mail inválido: " + email);
        }
        return email.toLowerCase(Locale.ROOT);
    }

    /** Aceita "(21) 98765-4321", "+55 21 98765-4321" etc.; guarda os dígitos com DDD. */
    private static String phone(String value) {
        String raw = blankToNull(value);
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("55") && digits.length() > 11) {
            digits = digits.substring(2);
        }
        if (digits.length() < 10 || digits.length() > 11) {
            throw new DomainRuleViolation("PHONE_INVALID", "Telefone com DDD, 10 ou 11 dígitos: " + raw);
        }
        return digits;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() {
        return id;
    }

    public UUID getAgencyId() {
        return agencyId;
    }

    public int getPosition() {
        return position;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getNotes() {
        return notes;
    }
}
