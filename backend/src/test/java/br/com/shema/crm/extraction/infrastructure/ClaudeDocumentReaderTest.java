package br.com.shema.crm.extraction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.shema.crm.extraction.application.DocumentReader.ReadRequest;
import br.com.shema.crm.extraction.application.ExtractionProperties;
import br.com.shema.crm.extraction.domain.Extraction;
import br.com.shema.crm.extraction.domain.RawReading;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Monta a requisição sem chamar a API: o SDK confere localmente se o esquema de {@link RawReading} é aceito pela
 * saída estruturada, então um campo de tipo não suportado quebra aqui, e não em produção.
 */
class ClaudeDocumentReaderTest {

    private static ExtractionProperties properties(boolean fallbacks) {
        return new ExtractionProperties(
                "chave-de-teste", "claude-opus-5-5", "medium", 32000, 5, fallbacks, "Shema", 20);
    }

    private static ClaudeDocumentReader reader(ExtractionProperties properties) {
        return new ClaudeDocumentReader(
                AnthropicOkHttpClient.builder().apiKey("chave-de-teste").build(), properties);
    }

    private static final ReadRequest REQUEST =
            new ReadRequest("%PDF-1.7 ata".getBytes(StandardCharsets.US_ASCII), "ata.pdf", Extraction.Kind.ATA);

    @Test
    void sendsThePdfWithTheStructuredSchemaAndTheFallback() {
        StructuredMessageCreateParams<RawReading> params =
                reader(properties(true)).params(REQUEST);

        assertThat(params.rawParams().model().asString()).isEqualTo("claude-opus-5-5");
        assertThat(params.rawParams().maxTokens()).isEqualTo(32000);
        assertThat(params.rawParams().system().orElseThrow().asString()).contains("Shema");
        assertThat(params.rawParams()
                        .outputConfig()
                        .orElseThrow()
                        .effort()
                        .orElseThrow()
                        .asString())
                .isEqualTo("medium");
        assertThat(params.rawParams().outputConfig().orElseThrow().format()).isPresent();
        assertThat(params.rawParams().messages().getFirst().content().asContentBlockParams())
                .hasSize(2)
                .first()
                .satisfies(block -> assertThat(block.isDocument()).isTrue());
        assertThat(params.rawParams()._additionalHeaders().values("anthropic-beta"))
                .containsExactly(ClaudeDocumentReader.FALLBACK_BETA);
        assertThat(params.rawParams()._additionalBodyProperties())
                .containsEntry("fallbacks", JsonValue.from("default"));
    }

    @Test
    void withoutFallbacksTheRequestHasNoBeta() {
        StructuredMessageCreateParams<RawReading> params =
                reader(properties(false)).params(REQUEST);

        assertThat(params.rawParams()._additionalHeaders().values("anthropic-beta"))
                .isEmpty();
        assertThat(params.rawParams()._additionalBodyProperties()).doesNotContainKey("fallbacks");
    }
}
