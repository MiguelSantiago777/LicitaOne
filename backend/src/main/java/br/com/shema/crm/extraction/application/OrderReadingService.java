package br.com.shema.crm.extraction.application;

import br.com.shema.crm.contracts.ContractOrderContext;
import br.com.shema.crm.contracts.ContractOrderContext.OrderContext;
import br.com.shema.crm.extraction.OrderReadingRequested;
import br.com.shema.crm.extraction.domain.Extraction;
import br.com.shema.crm.extraction.domain.OrderReading;
import br.com.shema.crm.extraction.domain.OrderReadingResult;
import br.com.shema.crm.extraction.infrastructure.OrderReadingRepository;
import br.com.shema.crm.pricerecords.DocumentFiles;
import br.com.shema.crm.pricerecords.DocumentFiles.DocumentFile;
import br.com.shema.crm.shared.domain.BrFormat;
import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.messaging.EventMetadata;
import br.com.shema.crm.shared.security.CurrentUser;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pedido a partir de PDF, foto ou mensagem (Fase 8): põe a leitura na fila e devolve o resultado. Quem pode alterar a
 * licitação pede; todos consultam. O documento ou o texto só sai do sistema quando alguém clica em "Ler com IA".
 */
@Service
@Transactional
public class OrderReadingService {

    /** Tipos de documento que podem trazer um pedido: Empenho (nota de empenho, AF) e Outros (ofício, foto). */
    private static final Set<String> ORDER_KINDS = Set.of("EMPENHO", "OUTROS");

    private static final Set<String> READABLE = Set.of("application/pdf", "image/png", "image/jpeg");

    /** Texto colado: um e-mail longo com assinatura e histórico cabe com folga. */
    public static final int MAX_TEXT = 20_000;

    private static final List<Extraction.Status> IN_PROGRESS =
            List.of(Extraction.Status.QUEUED, Extraction.Status.RUNNING);

    @Schema(name = "OrderReadingResponse")
    public record OrderReadingView(
            UUID id,
            UUID contractId,
            OrderReading.Source source,
            UUID documentId,
            Integer documentVersion,
            Extraction.Status status,
            OrderReadingResult result,
            String model,
            Long inputTokens,
            Long outputTokens,
            String errorMessage,
            String createdByName,
            Instant createdAt,
            Instant finishedAt) {}

    private final OrderReadingRepository readings;
    private final ContractOrderContext contracts;
    private final DocumentFiles files;
    private final DocumentReader reader;
    private final ExtractionProperties properties;
    private final ApplicationEventPublisher publisher;
    private final JsonMapper json;
    private final Clock clock;

    public OrderReadingService(
            OrderReadingRepository readings,
            ContractOrderContext contracts,
            DocumentFiles files,
            DocumentReader reader,
            ExtractionProperties properties,
            ApplicationEventPublisher publisher,
            JsonMapper json,
            Clock clock) {
        this.readings = readings;
        this.contracts = contracts;
        this.files = files;
        this.reader = reader;
        this.properties = properties;
        this.publisher = publisher;
        this.json = json;
        this.clock = clock;
    }

    /** Lê um documento (PDF ou foto) já enviado em Documentos. A mesma versão em andamento não é mandada de novo. */
    public OrderReadingView fromDocument(UUID contractId, UUID documentId) {
        CurrentUser user = CurrentUser.get();
        OrderContext contract = openContract(contractId, user);
        DocumentFile file = files.current(documentId)
                .filter(f -> f.tenderId().equals(contract.tenderId()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Documento não encontrado nesta licitação."));
        if (!ORDER_KINDS.contains(file.kind())) {
            throw new DomainRuleViolation(
                    "AI_KIND_NOT_SUPPORTED",
                    "Para ler um pedido, envie o documento como Empenho ou Outros (ofício, AF, foto).");
        }
        if (!READABLE.contains(file.contentType())) {
            throw new DomainRuleViolation("AI_FILE_TYPE", "A IA lê pedidos em PDF, JPEG ou PNG.");
        }
        if (file.sizeBytes() > properties.maxFileBytes()) {
            throw new DomainRuleViolation(
                    "AI_FILE_TOO_LARGE",
                    "O arquivo passa de " + properties.maxFileMb() + " MB, o máximo que a IA lê de uma vez.");
        }
        Optional<OrderReading> running = readings.findFirstByContractIdAndDocumentIdAndDocumentVersionAndStatusIn(
                contractId, documentId, file.versionNumber(), IN_PROGRESS);
        if (running.isPresent()) {
            return view(running.get());
        }
        return queue(OrderReading.ofDocument(
                contractId,
                contract.tenderId(),
                documentId,
                file.versionNumber(),
                user.id(),
                user.name(),
                clock.instant()));
    }

    /** Lê um e-mail ou mensagem colada. O texto é apagado quando a leitura termina. */
    public OrderReadingView fromText(UUID contractId, String text) {
        CurrentUser user = CurrentUser.get();
        OrderContext contract = openContract(contractId, user);
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.length() < 10) {
            throw new DomainRuleViolation("AI_TEXT_EMPTY", "Cole a mensagem com o pedido.");
        }
        if (trimmed.length() > MAX_TEXT) {
            throw new DomainRuleViolation(
                    "AI_TEXT_TOO_LONG", "A mensagem passa de 20 mil caracteres. Cole só a parte com o pedido.");
        }
        return queue(
                OrderReading.ofText(contractId, contract.tenderId(), trimmed, user.id(), user.name(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public OrderReadingView get(UUID readingId) {
        return view(readings.findById(readingId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Leitura não encontrada.")));
    }

    String toJson(OrderReadingResult result) {
        return json.writeValueAsString(result);
    }

    /** O contrato como o modelo vê: cabeçalho e itens numerados com unidade, preço e saldo. */
    static String describe(OrderContext c) {
        StringBuilder text = new StringBuilder();
        text.append("Órgão: ").append(c.agencyName()).append('\n');
        text.append(c.kindLabel())
                .append(c.number() == null ? " sem número" : " nº " + c.number())
                .append(", assinado em ")
                .append(BrFormat.date(c.signedAt()))
                .append('\n');
        text.append("Itens (número | lote | descrição | unidade | preço unitário | saldo a pedir):\n");
        List<ContractOrderContext.Item> items = c.items();
        for (int i = 0; i < items.size(); i++) {
            ContractOrderContext.Item item = items.get(i);
            text.append(i + 1)
                    .append(" | ")
                    .append(item.lot() == null ? "-" : item.lot())
                    .append(" | ")
                    .append(item.description())
                    .append(" | ")
                    .append(item.unit() == null ? "-" : item.unit())
                    .append(" | ")
                    .append(item.unitPrice().toPlainString())
                    .append(" | ")
                    .append(item.remaining().toPlainString())
                    .append('\n');
        }
        return text.toString();
    }

    static OrderReadingResult.Contract forCheck(OrderContext c) {
        return new OrderReadingResult.Contract(
                c.agencyName(),
                c.number(),
                c.signedAt(),
                c.items().stream()
                        .map(i -> new OrderReadingResult.Item(i.id(), i.description(), i.unitPrice(), i.remaining()))
                        .toList());
    }

    private OrderContext openContract(UUID contractId, CurrentUser user) {
        OrderContext contract = contracts
                .find(contractId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Contrato não encontrado."));
        user.requireCanEditOwnedBy(contract.ownerId());
        if (!reader.enabled()) {
            throw new DomainRuleViolation(
                    "AI_DISABLED", "A leitura com IA não está configurada neste ambiente. Fale com o administrador.");
        }
        if (contract.closed()) {
            throw new DomainRuleViolation("CONTRACT_CLOSED", "Este contrato já foi encerrado.");
        }
        if (contract.items().isEmpty()) {
            throw new DomainRuleViolation(
                    "AI_CONTRACT_WITHOUT_ITEMS",
                    "Cadastre os itens do contrato antes: a IA relaciona o pedido com eles.");
        }
        return contract;
    }

    private OrderReadingView queue(OrderReading reading) {
        readings.saveAndFlush(reading);
        publisher.publishEvent(new OrderReadingRequested(EventMetadata.create(clock), reading.getId()));
        return view(reading);
    }

    private OrderReadingView view(OrderReading r) {
        OrderReadingResult result =
                r.getResult() == null ? null : json.readValue(r.getResult(), OrderReadingResult.class);
        return new OrderReadingView(
                r.getId(),
                r.getContractId(),
                r.getSource(),
                r.getDocumentId(),
                r.getDocumentVersion(),
                r.getStatus(),
                result,
                r.getModel(),
                r.getInputTokens(),
                r.getOutputTokens(),
                r.getErrorMessage(),
                r.getCreatedByName(),
                r.getCreatedAt(),
                r.getFinishedAt());
    }
}
