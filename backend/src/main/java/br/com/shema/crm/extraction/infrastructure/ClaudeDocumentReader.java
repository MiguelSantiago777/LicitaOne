package br.com.shema.crm.extraction.infrastructure;

import br.com.shema.crm.extraction.application.DocumentReader;
import br.com.shema.crm.extraction.application.ExtractionProperties;
import br.com.shema.crm.extraction.domain.RawReading;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonSchemaLocalValidation;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.StructuredTextBlock;
import com.anthropic.models.messages.TextBlockParam;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Lê o PDF com o Claude (API da Anthropic, SDK oficial): o documento vai em base64 e a resposta sai no esquema de
 * {@link RawReading} (saída estruturada). O SDK já tenta de novo erros passageiros (429, 5xx, rede).
 *
 * <p>Com {@code fallbacks}, o servidor da Anthropic tenta outro modelo quando o principal recusa o documento por
 * política de segurança (falso positivo é raro, mas acontece), e a resposta diz qual modelo leu.
 */
class ClaudeDocumentReader implements DocumentReader {

    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final AnthropicClient client;
    private final ExtractionProperties properties;

    ClaudeDocumentReader(ExtractionProperties properties) {
        this(
                AnthropicOkHttpClient.builder()
                        .apiKey(properties.apiKey())
                        .timeout(Duration.ofMinutes(properties.timeoutMinutes()))
                        .maxRetries(2)
                        .build(),
                properties);
    }

    ClaudeDocumentReader(AnthropicClient client, ExtractionProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Reading read(ReadRequest request) {
        StructuredMessage<RawReading> message;
        try {
            message = client.messages().create(params(request));
        } catch (UnauthorizedException | PermissionDeniedException e) {
            throw new ReadingFailed("A chave da IA foi recusada. Fale com o administrador.", null, null, null, e);
        } catch (BadRequestException e) {
            throw new ReadingFailed(
                    "A IA não conseguiu abrir este PDF (protegido por senha, corrompido ou grande demais).",
                    null,
                    null,
                    null,
                    e);
        } catch (RateLimitException | AnthropicIoException e) {
            throw new ReadingFailed(
                    "A IA está ocupada ou fora do ar agora. Tente de novo em alguns minutos.", null, null, null, e);
        } catch (AnthropicServiceException e) {
            throw new ReadingFailed(
                    "A IA respondeu com erro (" + e.statusCode() + "). Tente de novo em alguns minutos.",
                    null,
                    null,
                    null,
                    e);
        }

        String model = message.model().asString();
        long input = message.usage().inputTokens();
        long output = message.usage().outputTokens();
        Optional<StopReason> stop = message.stopReason();
        if (stop.filter(StopReason.REFUSAL::equals).isPresent()) {
            throw new ReadingFailed("A IA não aceitou ler este documento.", model, input, output, null);
        }
        if (stop.filter(StopReason.MAX_TOKENS::equals).isPresent()) {
            throw new ReadingFailed(
                    "O documento tem mais itens do que a IA consegue ler de uma vez. Cadastre os itens pela planilha.",
                    model,
                    input,
                    output,
                    null);
        }
        RawReading raw = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(StructuredTextBlock::text)
                .findFirst()
                .orElseThrow(() -> new ReadingFailed(
                        "A IA não devolveu nenhum dado deste documento.", model, input, output, null));
        return new Reading(raw, model, input, output);
    }

    StructuredMessageCreateParams<RawReading> params(ReadRequest request) {
        DocumentBlockParam document = DocumentBlockParam.builder()
                .source(Base64PdfSource.builder()
                        .data(Base64.getEncoder().encodeToString(request.pdf()))
                        .build())
                .title(request.fileName())
                .build();
        StructuredMessageCreateParams.Builder<RawReading> builder = MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(properties.maxTokens())
                .system(ExtractionPrompt.system(properties.companyName()))
                .outputConfig(StructuredOutputConfig.<RawReading>builder()
                        .effort(OutputConfig.Effort.of(properties.effort()))
                        .format(RawReading.class, JsonSchemaLocalValidation.YES)
                        .build())
                .addUserMessageOfContentBlockParams(List.of(
                        ContentBlockParam.ofDocument(document),
                        ContentBlockParam.ofText(TextBlockParam.builder()
                                .text(ExtractionPrompt.user(request.kind()))
                                .build())));
        if (properties.fallbacks()) {
            builder.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        return builder.build();
    }
}
