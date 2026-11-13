package br.com.shema.crm.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BrDocumentsTest {

    @Test
    void cpfKeepsOnlyDigits() {
        assertThat(BrDocuments.cpf("529.982.247-25")).isEqualTo("52998224725");
        assertThat(BrDocuments.cpf(" ")).isNull();
        assertThat(BrDocuments.formatCpf("52998224725")).isEqualTo("529.982.247-25");
    }

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-24", "111.111.111-11", "1234567890", "abc"})
    void invalidCpfIsARuleViolation(String value) {
        assertThatThrownBy(() -> BrDocuments.cpf(value))
                .isInstanceOf(DomainRuleViolation.class)
                .hasMessageContaining("CPF inválido");
    }

    @Test
    void numericCnpj() {
        assertThat(BrDocuments.cnpj("11.222.333/0001-81")).isEqualTo("11222333000181");
        assertThat(BrDocuments.formatCnpj("11222333000181")).isEqualTo("11.222.333/0001-81");
        assertThat(BrDocuments.cnpj(null)).isNull();
    }

    @Test
    void alphanumericCnpjFromJuly2026() {
        // Exemplo da Receita Federal para o CNPJ alfanumérico
        assertThat(BrDocuments.cnpj("12.abc.345/01de-35")).isEqualTo("12ABC34501DE35");
        assertThat(BrDocuments.formatCnpj("12ABC34501DE35")).isEqualTo("12.ABC.345/01DE-35");
    }

    @ParameterizedTest
    @ValueSource(strings = {"11.222.333/0001-82", "00.000.000/0000-00", "12ABC34501DE3A", "123"})
    void invalidCnpjIsARuleViolation(String value) {
        assertThatThrownBy(() -> BrDocuments.cnpj(value))
                .isInstanceOf(DomainRuleViolation.class)
                .hasMessageContaining("CNPJ inválido");
    }
}
