package br.com.shema.crm.identity.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Regras de senha do OWASP ASVS nível 1 (V2.1):
 * <ul>
 *   <li>de 12 a 128 caracteres, contados como o usuário vê;</li>
 *   <li>qualquer caractere vale, inclusive espaço e acento (frase-senha é bem-vinda);</li>
 *   <li>não pode ser uma senha comum de vazamentos, nem repetição ou sequência de teclado;</li>
 *   <li>não pode conter o e-mail da pessoa.</li>
 * </ul>
 * Não há regra de "uma maiúscula, um número": o ASVS desaconselha.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    /**
     * Senhas de 12 caracteres ou mais que aparecem no topo das listas de vazamento (incluindo as brasileiras). As
     * curtas já caem pelo tamanho.
     */
    private static final Set<String> COMMON = Set.of(
            "123456789012",
            "1234567890123",
            "12345678910111213",
            "123123123123",
            "123456123456",
            "112233445566",
            "000000000000",
            "qwertyuiop12",
            "qwertyuiop123",
            "qwerty123456",
            "1q2w3e4r5t6y",
            "1qaz2wsx3edc",
            "q1w2e3r4t5y6",
            "zaq12wsxcde3",
            "asdfghjkl123",
            "password1234",
            "password12345",
            "password123456",
            "passw0rd1234",
            "iloveyou1234",
            "senha1234567",
            "senha12345678",
            "senha123456789",
            "minhasenha123",
            "minhasenha1234",
            "senhasegura123",
            "senhaforte123",
            "mudar1234567",
            "trocar123456",
            "admin1234567",
            "administrador",
            "administrador1",
            "administrador123",
            "brasil123456",
            "flamengo1234",
            "corinthians1",
            "corinthians123",
            "palmeiras123",
            "saopaulo1234",
            "vasco1234567",
            "botafogo1234",
            "fluminense123",
            "abcdefghijkl",
            "abc123456789",
            "a1b2c3d4e5f6",
            "licitaone123",
            "licitaone2026",
            "shema1234567",
            "shemacrm1234",
            "shemasuprimentos");

    private PasswordPolicy() {}

    /** O motivo da recusa, pronto para mostrar na tela; vazio se a senha serve. */
    public static Optional<String> problem(String password, String email) {
        if (password == null || password.isEmpty()) {
            return Optional.of("Informe a senha.");
        }
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH) {
            return Optional.of("A senha precisa ter pelo menos " + MIN_LENGTH + " caracteres.");
        }
        if (length > MAX_LENGTH) {
            return Optional.of("Use no máximo " + MAX_LENGTH + " caracteres na senha.");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower) || isRepetition(lower) || isSequence(lower)) {
            return Optional.of("Essa senha é muito comum e fácil de adivinhar. Tente uma frase com algumas palavras.");
        }
        if (email != null) {
            String user = email.toLowerCase(Locale.ROOT).split("@")[0];
            if (user.length() >= 4 && lower.contains(user)) {
                return Optional.of("A senha não pode conter o seu e-mail.");
            }
        }
        return Optional.empty();
    }

    /** "aaaaaaaaaaaa", "abcabcabcabc": um bloco curto repetido. */
    private static boolean isRepetition(String s) {
        for (int block = 1; block <= 4; block++) {
            if (s.length() % block == 0 && s.equals(s.substring(0, block).repeat(s.length() / block))) {
                return true;
            }
        }
        return false;
    }

    /** "abcdefghijklm", "987654321098": cada caractere é o seguinte (ou o anterior) do outro, com volta no 9→0. */
    private static boolean isSequence(String s) {
        int up = 0;
        int down = 0;
        for (int i = 1; i < s.length(); i++) {
            int diff = s.charAt(i) - s.charAt(i - 1);
            boolean digitWrapUp = s.charAt(i - 1) == '9' && s.charAt(i) == '0';
            boolean digitWrapDown = s.charAt(i - 1) == '0' && s.charAt(i) == '9';
            if (diff == 1 || digitWrapUp) {
                up++;
            }
            if (diff == -1 || digitWrapDown) {
                down++;
            }
        }
        return up == s.length() - 1 || down == s.length() - 1;
    }
}
