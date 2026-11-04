package br.com.shema.crm.extraction.application;

import br.com.shema.crm.contracts.ContractOrderContext;
import br.com.shema.crm.contracts.ContractOrderContext.OrderContext;
import br.com.shema.crm.extraction.application.DocumentReader.Answer;
import br.com.shema.crm.extraction.application.DocumentReader.FollowUpRequest;
import br.com.shema.crm.extraction.application.DocumentReader.ReadingFailed;
import br.com.shema.crm.extraction.application.DocumentReader.ResponseRequest;
import br.com.shema.crm.extraction.domain.Extraction;
import br.com.shema.crm.extraction.domain.FollowUpDraft;
import br.com.shema.crm.extraction.domain.RawFollowUp;
import br.com.shema.crm.extraction.domain.RawResponse;
import br.com.shema.crm.extraction.domain.ResponseReading;
import br.com.shema.crm.shared.domain.BrFormat;
import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.security.CurrentUser;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import br.com.shema.crm.tenders.AgencyDirectory;
import br.com.shema.crm.tenders.TenderContacts;
import br.com.shema.crm.tenders.TenderContacts.ContactRef;
import br.com.shema.crm.tenders.TenderLifecycle;
import br.com.shema.crm.tenders.TenderRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Follow-up com contexto e resposta do órgão (Fase 9, ADR 0018). As duas chamadas são curtas e sem documento, então
 * rodam na hora (sem fila), com a pessoa esperando no diálogo. Nada é gravado aqui: o resultado é uma sugestão que a
 * pessoa confere e salva pelos endpoints de sempre. A resposta colada nunca vai para os logs.
 *
 * <p>Sem transação: a chamada ao modelo leva segundos e não deve segurar conexão do banco. Os dados de contexto são
 * lidos pelas APIs públicas, cada uma com a sua transação curta.
 */
@Service
public class FollowUpAssistant {

    private static final Logger log = LoggerFactory.getLogger(FollowUpAssistant.class);

    /** Contatos recentes que entram no contexto. */
    static final int RECENT_CONTACTS = 5;

    /** Itens com mais saldo citados no contexto do follow-up. */
    static final int TOP_ITEMS = 5;

    public static final int MAX_TEXT = 20_000;

    @Schema(name = "ResponseReadingResponse")
    public record ResponseView(ResponseReading result, String model) {}

    /**
     * @param to e-mails dos contatos do órgão, separados por vírgula
     * @param phone primeiro telefone dos contatos do órgão, para o "Abrir no WhatsApp"
     */
    @Schema(name = "FollowUpDraftResponse")
    public record FollowUpView(FollowUpDraft draft, String to, String phone, String model) {}

    private final DocumentReader reader;
    private final TenderLifecycle tenders;
    private final TenderContacts contacts;
    private final AgencyDirectory agencies;
    private final ContractOrderContext contracts;
    private final ExtractionMetrics metrics;
    private final Clock clock;

    FollowUpAssistant(
            DocumentReader reader,
            TenderLifecycle tenders,
            TenderContacts contacts,
            AgencyDirectory agencies,
            ContractOrderContext contracts,
            ExtractionMetrics metrics,
            Clock clock) {
        this.reader = reader;
        this.tenders = tenders;
        this.contacts = contacts;
        this.agencies = agencies;
        this.contracts = contracts;
        this.metrics = metrics;
        this.clock = clock;
    }

    /** Entende a resposta colada do órgão: situação, data prevista, resumo e próxima ação. */
    public ResponseView interpret(UUID tenderId, String text, LocalDate contactedAt, String channel) {
        TenderRef tender = editable(tenderId);
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.length() < 5) {
            throw new DomainRuleViolation("AI_TEXT_EMPTY", "Cole a resposta do órgão.");
        }
        if (trimmed.length() > MAX_TEXT) {
            throw new DomainRuleViolation(
                    "AI_TEXT_TOO_LONG", "A resposta passa de 20 mil caracteres. Cole só a parte que importa.");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate date = contactedAt == null || contactedAt.isAfter(today) ? today : contactedAt;
        String context = context(tender, contracts.openForTender(tenderId), null) + "\nContato de "
                + BrFormat.date(date) + (channel == null ? "" : " por " + channel) + ".\n";
        Answer<RawResponse> answer = call("RESPOSTA", () -> reader.readResponse(new ResponseRequest(trimmed, context)));
        return new ResponseView(ResponseReading.from(answer.raw(), date, today), answer.model());
    }

    /**
     * Escreve o follow-up ao órgão.
     *
     * @param contractId contrato de que se fala; nulo para todos os contratos abertos da licitação
     * @param tone NORMAL, CURTO ou FORMAL
     */
    public FollowUpView followUp(UUID tenderId, UUID contractId, boolean whatsapp, String tone) {
        TenderRef tender = editable(tenderId);
        List<OrderContext> open = contracts.openForTender(tenderId).stream()
                .filter(c -> contractId == null || c.contractId().equals(contractId))
                .toList();
        if (open.isEmpty()) {
            throw new DomainRuleViolation(
                    "AI_NO_OPEN_CONTRACT", "Esta licitação não tem contrato aberto para o follow-up.");
        }
        String context = context(tender, open, contractId);
        String signature = CurrentUser.get().name();
        String safeTone = List.of("NORMAL", "CURTO", "FORMAL").contains(tone) ? tone : "NORMAL";
        Answer<RawFollowUp> answer = call(
                "RASCUNHO", () -> reader.writeFollowUp(new FollowUpRequest(context, whatsapp, safeTone, signature)));
        FollowUpDraft draft;
        try {
            draft = FollowUpDraft.from(answer.raw(), whatsapp);
        } catch (IllegalArgumentException e) {
            throw new DomainRuleViolation("AI_FAILED", "A IA não escreveu o texto. Tente de novo.");
        }
        List<String> emails = agencies.contactEmails(tender.agencyId());
        List<String> phones = agencies.contactPhones(tender.agencyId());
        return new FollowUpView(
                draft,
                emails.isEmpty() ? null : String.join(", ", emails),
                phones.isEmpty() ? null : phones.getFirst(),
                answer.model());
    }

    private TenderRef editable(UUID tenderId) {
        TenderRef tender = tenders.find(tenderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Licitação não encontrada."));
        CurrentUser.get().requireCanEditOwnedBy(tender.ownerId());
        if (!reader.enabled()) {
            throw new DomainRuleViolation(
                    "AI_DISABLED", "A IA não está configurada neste ambiente. Fale com o administrador.");
        }
        return tender;
    }

    private <T> Answer<T> call(String kind, Supplier<Answer<T>> request) {
        try {
            Answer<T> answer = request.get();
            metrics.finished(kind, Extraction.Status.DONE, answer.inputTokens(), answer.outputTokens());
            return answer;
        } catch (ReadingFailed e) {
            log.warn("IA ({}) falhou: {}", kind, e.getMessage(), e.getCause());
            metrics.finished(kind, Extraction.Status.FAILED, e.inputTokens(), e.outputTokens());
            throw new DomainRuleViolation("AI_FAILED", e.getMessage());
        }
    }

    /** O que o modelo sabe da licitação: órgão, objeto, contratos com saldo e último pedido, e os últimos contatos. */
    private String context(TenderRef tender, List<OrderContext> open, UUID focus) {
        StringBuilder text = new StringBuilder();
        text.append("Hoje: ").append(BrFormat.date(LocalDate.now(clock))).append('\n');
        text.append("Órgão: ").append(tender.agencyName()).append('\n');
        text.append("Licitação: ")
                .append(tender.identification())
                .append(" · ")
                .append(tender.object())
                .append('\n');
        if (open.isEmpty()) {
            text.append("Nenhum contrato aberto.\n");
        }
        for (OrderContext c : open) {
            text.append('\n')
                    .append(c.kindLabel())
                    .append(c.number() == null ? " sem número" : " nº " + c.number())
                    .append(focus != null && focus.equals(c.contractId()) ? " (o follow-up é sobre este)" : "")
                    .append(", assinado em ")
                    .append(BrFormat.date(c.signedAt()))
                    .append(c.validUntil() == null ? "" : ", vigente até " + BrFormat.date(c.validUntil()))
                    .append('\n');
            text.append("Valor ")
                    .append(BrFormat.money(c.value()))
                    .append(", pedido ")
                    .append(BrFormat.money(c.ordered()))
                    .append(", saldo a pedir ")
                    .append(BrFormat.money(c.remaining()))
                    .append('\n');
            text.append(
                    c.lastOrderAt() == null
                            ? "Nenhum pedido até agora.\n"
                            : "Último pedido em " + BrFormat.date(c.lastOrderAt()) + ".\n");
            List<ContractOrderContext.Item> top = c.items().stream()
                    .filter(i -> i.remaining().signum() > 0)
                    .sorted(Comparator.comparing((ContractOrderContext.Item i) ->
                                    i.remaining().multiply(i.unitPrice()))
                            .reversed())
                    .limit(TOP_ITEMS)
                    .toList();
            if (!top.isEmpty()) {
                text.append("Itens com mais saldo: ");
                text.append(String.join(
                        "; ",
                        top.stream()
                                .map(i -> i.description() + " (" + BrFormat.quantity(i.remaining())
                                        + (i.unit() == null ? "" : " " + i.unit()) + ")")
                                .toList()));
                text.append('\n');
            }
        }
        List<ContactRef> recent = contacts.recent(tender.id(), RECENT_CONTACTS);
        text.append('\n');
        if (recent.isEmpty()) {
            text.append("Nenhum contato registrado com o órgão.\n");
        } else {
            text.append("Últimos contatos (do mais recente):\n");
            for (ContactRef r : recent) {
                text.append("- ")
                        .append(BrFormat.date(r.contactedAt()))
                        .append(", ")
                        .append(r.channel())
                        .append(r.outcome() == null ? "" : " (" + r.outcome() + ")")
                        .append(": ")
                        .append(r.summary())
                        .append('\n');
            }
        }
        return text.toString();
    }
}
