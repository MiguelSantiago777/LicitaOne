package br.com.shema.crm.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AmountInWordsTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "0|zero real",
                "0.01|um centavo",
                "0.50|cinquenta centavos",
                "1|um real",
                "2|dois reais",
                "1.01|um real e um centavo",
                "15|quinze reais",
                "21|vinte e um reais",
                "100|cem reais",
                "101|cento e um reais",
                "110.90|cento e dez reais e noventa centavos",
                "999|novecentos e noventa e nove reais",
                "1000|mil reais",
                "1001|mil e um reais",
                "1100|mil e cem reais",
                "1234.56|mil duzentos e trinta e quatro reais e cinquenta e seis centavos",
                "2005|dois mil e cinco reais",
                "21000|vinte e um mil reais",
                "237000|duzentos e trinta e sete mil reais",
                "1000000|um milhão de reais",
                "2000000|dois milhões de reais",
                "1000100|um milhão e cem reais",
                "1500000|um milhão e quinhentos mil reais",
                "1250300.10|um milhão duzentos e cinquenta mil e trezentos reais e dez centavos",
                "3000000000|três bilhões de reais"
            })
    void writesTheAmountInPortuguese(String value, String expected) {
        assertThat(AmountInWords.reais(new BigDecimal(value))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"-1", "1000000000000"})
    void rejectsNegativeAndHugeValues(String value) {
        assertThatThrownBy(() -> AmountInWords.reais(new BigDecimal(value)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
