package br.com.shema.crm.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtKeyConfigTest {

    @Test
    void productionRefusesToStartWithoutTheSigningKey() {
        assertThatThrownBy(() -> new JwtKeyConfig().jwtSigningKey(new JwtProperties(" ", "prod-1", true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SHEMA_JWT_PRIVATE_KEY");
    }

    @Test
    void developmentGeneratesATemporaryKey() {
        assertThat(new JwtKeyConfig()
                        .jwtSigningKey(new JwtProperties(null, "dev-1", false))
                        .getKeyID())
                .isEqualTo("dev-1");
    }
}
