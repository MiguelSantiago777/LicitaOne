package br.com.shema.crm.extraction.infrastructure;

import br.com.shema.crm.extraction.application.DocumentReader;
import br.com.shema.crm.extraction.application.ExtractionProperties;
import br.com.shema.crm.extraction.domain.RawOrder;
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
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.PlainTextSource;
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
 * Lê documentos com o Claude (API da Anthropic, SDK oficial), com a resposta no esquema de {@link RawReading} (ata e
 * contrato) ou de {@link RawOrder} (pedido), por saída estruturada. O SDK já tenta de novo erros passageiros (429,
 * 5xx, rede).
 *
 * <p>O pedido pode chegar como PDF, foto ou texto colado. Fotos grandes são reduzidas antes do envio
 * ({@link ImageShrinker}); o texto colado vai como documento de texto, separado das instruções, para o modelo tratar
 * como dado e não como ordem.
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
        Answer<RawReading> answer = call(params(request));
        return new Reading(answer.value(), answer.model(), answer.input(), answer.output());
    }

    @Override
    public OrderRead readOrder(OrderReadRequest request) {
        Answer<RawOrder> answer = call(orderParams(request));
        return new OrderRead(answer.value(), answer.model(), answer.input(), answer.output());
    }

    private record Answer<T>(T value, String model, long input, long output) {}

    private <T> Answer<T> call(StructuredMessageCreateParams<T> params) {
        StructuredMessage<T> message;
        try {
            message = client.messages().create(params);
        } catch (UnauthorizedException | PermissionDeniedException e) {
            throw new ReadingFailed("A chave da IA foi recusada. Fale com o administrador.", null, null, null, e);
        } catch (BadRequestException e) {
            throw new ReadingFailed(
                    "A IA não conseguiu abrir este arquivo (protegido por senha, corrompido ou grande demais).",
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
        T value = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(StructuredTextBlock::text)
                .findFirst()
                .orElseThrow(() -> new ReadingFailed(
                        "A IA não devolveu nenhum dado deste documento.", model, input, output, null));
        return new Answer<>(value, model, input, output);
    }

    StructuredMessageCreateParams<RawReading> params(ReadRequest request) {
        DocumentBlockParam document = DocumentBlockParam.builder()
                .source(Base64PdfSource.builder()
                        .data(Base64.getEncoder().encodeToString(request.pdf()))
                        .build())
                .title(request.fileName())
                .build();
        return build(
                RawReading.class,
                ExtractionPrompt.system(properties.companyName()),
                List.of(ContentBlockParam.ofDocument(document), text(ExtractionPrompt.user(request.kind()))));
    }

    StructuredMessageCreateParams<RawOrder> orderParams(OrderReadRequest request) {
        return build(
                RawOrder.class,
                ExtractionPrompt.orderSystem(properties.companyName()),
                List.of(
                        text(ExtractionPrompt.orderContract(request.contract())),
                        source(request),
                        text(ExtractionPrompt.orderUser(request.isText()))));
    }

    /** O pedido em si: PDF, imagem (reduzida se for grande) ou o texto colado como documento de texto. */
    private static ContentBlockParam source(OrderReadRequest request) {
        if (request.isText()) {
            return ContentBlockParam.ofDocument(DocumentBlockParam.builder()
                    .source(PlainTextSource.builder().data(request.text()).build())
                    .title("Mensagem colada")
                    .build());
        }
        if ("application/pdf".equals(request.contentType())) {
            return ContentBlockParam.ofDocument(DocumentBlockParam.builder()
                    .source(Base64PdfSource.builder()
                            .data(Base64.getEncoder().encodeToString(request.content()))
                            .build())
                    .title(request.fileName())
                    .build());
        }
        ImageShrinker.Image image = ImageShrinker.shrink(request.content(), request.contentType());
        return ContentBlockParam.ofImage(ImageBlockParam.builder()
                .source(Base64ImageSource.builder()
                        .mediaType(Base64ImageSource.MediaType.of(image.contentType()))
                        .data(Base64.getEncoder().encodeToString(image.bytes()))
                        .build())
                .build());
    }

    private static ContentBlockParam text(String value) {
        return ContentBlockParam.ofText(TextBlockParam.builder().text(value).build());
    }

    private <T> StructuredMessageCreateParams<T> build(
            Class<T> schema, String system, List<ContentBlockParam> content) {
        StructuredMessageCreateParams.Builder<T> builder = MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(properties.maxTokens())
                .system(system)
                .outputConfig(StructuredOutputConfig.<T>builder()
                        .effort(OutputConfig.Effort.of(properties.effort()))
                        .format(schema, JsonSchemaLocalValidation.YES)
                        .build())
                .addUserMessageOfContentBlockParams(content);
        if (properties.fallbacks()) {
            builder.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        return builder.build();
    }
}
