package br.com.shema.crm.extraction.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Dados da empresa nas cartas do kit de carona. Campo em branco vira o rótulo entre colchetes no texto ("[CNPJ da
 * empresa]"), para a pessoa ver o que falta preencher antes de mandar.
 */
public record CompanyData(
        String legalName,
        String cnpj,
        String address,
        String city,
        String representative,
        String representativeRole,
        String email,
        String phone) {

    public String legalNameOr() {
        return or(legalName, "[Razão social da empresa]");
    }

    public String cnpjOr() {
        return or(cnpj, "[CNPJ da empresa]");
    }

    public String addressOr() {
        return or(address, "[Endereço da empresa]");
    }

    public String cityOr() {
        return or(city, "[Cidade da empresa]");
    }

    public String representativeOr() {
        return or(representative, "[Nome do representante legal]");
    }

    public String representativeRoleOr() {
        return or(representativeRole, "[Cargo do representante]");
    }

    /** Contato para o rodapé da apresentação: e-mail e telefone que estiverem preenchidos. */
    public String contactLine() {
        List<String> parts = new ArrayList<>();
        if (!blank(email)) {
            parts.add(email.trim());
        }
        if (!blank(phone)) {
            parts.add(phone.trim());
        }
        return String.join(" · ", parts);
    }

    /** Os campos em branco, pelo nome que a tela mostra. */
    public List<String> missing() {
        List<String> missing = new ArrayList<>();
        if (blank(legalName)) {
            missing.add("razão social");
        }
        if (blank(cnpj)) {
            missing.add("CNPJ");
        }
        if (blank(address)) {
            missing.add("endereço");
        }
        if (blank(city)) {
            missing.add("cidade");
        }
        if (blank(representative)) {
            missing.add("representante legal");
        }
        if (blank(representativeRole)) {
            missing.add("cargo do representante");
        }
        return List.copyOf(missing);
    }

    private static String or(String value, String placeholder) {
        return blank(value) ? placeholder : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
