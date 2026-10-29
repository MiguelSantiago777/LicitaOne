package br.com.shema.crm.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void lengthIsTwelveToOneHundredTwentyEightCharactersAsTheUserSeesThem() {
        assertThat(PasswordPolicy.problem("curta-123", null)).contains("A senha precisa ter pelo menos 12 caracteres.");
        assertThat(PasswordPolicy.problem("pão de queijo", null)).isEmpty();
        // Emoji conta como um caractere
        assertThat(PasswordPolicy.problem("🔒🔒🔒🔒🔒🔒x", null)).isPresent();
        assertThat(PasswordPolicy.problem("x".repeat(64) + "y".repeat(64), null))
                .isEmpty();
        assertThat(PasswordPolicy.problem("xy".repeat(64) + "z", null)).isPresent();
    }

    @Test
    void anyCharacterIsAllowedAndThereIsNoCompositionRule() {
        assertThat(PasswordPolicy.problem("cavalo bateria grampo correto", null))
                .isEmpty();
        assertThat(PasswordPolicy.problem("licitação ganha em março", null)).isEmpty();
    }

    @Test
    void commonRepeatedAndSequentialPasswordsAreRefused() {
        for (String weak : new String[] {
            "Senha1234567",
            "123456789012",
            "QWERTYUIOP12",
            "aaaaaaaaaaaa",
            "abcabcabcabc",
            "abcdefghijklm",
            "987654321098",
            "LicitaOne2026"
        }) {
            assertThat(PasswordPolicy.problem(weak, null))
                    .as(weak)
                    .hasValueSatisfying(m -> assertThat(m).startsWith("Essa senha é muito comum"));
        }
    }

    @Test
    void passwordCannotContainTheEmail() {
        assertThat(PasswordPolicy.problem("ana.souza-2026!", "Ana.Souza@shema.local"))
                .contains("A senha não pode conter o seu e-mail.");
        assertThat(PasswordPolicy.problem("pão de queijo", "ana@shema.local")).isEmpty();
    }
}
