package br.com.shema.crm.tenders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.domain.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ValueObjectsTest {

    @Test
    void uasgKeepsOnlySixDigits() {
        assertThat(new Uasg(" 160.221 ").value()).isEqualTo("160221");
        assertThat(Uasg.ofNullable("  ")).isNull();
        assertThatThrownBy(() -> new Uasg("12345")).isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> new Uasg("1234567")).isInstanceOf(DomainRuleViolation.class);
    }

    @Test
    void processNumberIsNormalized() {
        assertThat(new ProcessNumber("39/2026").value()).isEqualTo("039/2026");
        assertThat(new ProcessNumber("Pregão Eletrônico nº 039 / 2026").value()).isEqualTo("039/2026");
        assertThat(new ProcessNumber("1234/2025").value()).isEqualTo("1234/2025");
        assertThat(ProcessNumber.ofNullable(null)).isNull();
        assertThatThrownBy(() -> new ProcessNumber("039-2026")).isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> new ProcessNumber("039/1990")).isInstanceOf(DomainRuleViolation.class);
    }

    @Test
    void moneyHasTwoDecimalsAndIsNeverNegative() {
        assertThat(Money.of("10.5").amount()).isEqualTo(new BigDecimal("10.50"));
        assertThat(Money.of("10.50").plus(Money.of("0.25"))).isEqualTo(Money.of("10.75"));
        assertThat(Money.ZERO.isZero()).isTrue();
        assertThat(Money.of("2").compareTo(Money.of("1"))).isPositive();
        assertThatThrownBy(() -> Money.of("-1")).isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> Money.of("1.005")).isInstanceOf(DomainRuleViolation.class);
    }

    @Test
    void agencyValidatesStateAndCnpj() {
        Agency agency = new Agency(
                " Colégio Pedro II ",
                Sphere.FEDERAL,
                "Rio de Janeiro",
                "rj",
                "33.663.683/0001-16",
                new Uasg("153167"),
                " ");
        assertThat(agency.getName()).isEqualTo("Colégio Pedro II");
        assertThat(agency.getState()).isEqualTo("RJ");
        assertThat(agency.getCnpj()).isEqualTo("33663683000116");
        assertThat(agency.getDefaultUasg().value()).isEqualTo("153167");
        assertThat(agency.getNotes()).isNull();
        assertThat(agency.getSphere().label()).isEqualTo("Federal");

        assertThatThrownBy(() -> new Agency("X", Sphere.MUNICIPAL, "Y", "XX", null, null, null))
                .isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> new Agency("X", Sphere.MUNICIPAL, "Y", "RJ", "123", null, null))
                .isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> new Agency(" ", Sphere.MUNICIPAL, "Y", "RJ", null, null, null))
                .isInstanceOf(DomainRuleViolation.class);
    }
}
