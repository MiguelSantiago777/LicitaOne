package br.com.shema.crm.extraction.application;

import br.com.shema.crm.extraction.application.DocumentReader.Answer;
import br.com.shema.crm.extraction.application.DocumentReader.CaronaKitRequest;
import br.com.shema.crm.extraction.application.DocumentReader.ReadingFailed;
import br.com.shema.crm.extraction.domain.CaronaLetters;
import br.com.shema.crm.extraction.domain.CaronaLetters.Kit;
import br.com.shema.crm.extraction.domain.CaronaLetters.KitLine;
import br.com.shema.crm.extraction.domain.Extraction;
import br.com.shema.crm.extraction.domain.RawCaronaKit;
import br.com.shema.crm.pricerecords.CaronaOffers;
import br.com.shema.crm.pricerecords.CaronaOffers.Offer;
import br.com.shema.crm.pricerecords.CaronaOffers.OfferItem;
import br.com.shema.crm.shared.company.CompanyProperties;
import br.com.shema.crm.shared.domain.BrFormat;
import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.security.CurrentUser;
import br.com.shema.crm.tenders.AgencyDirectory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Kit de carona para um município candidato (Fase 10, ADR 0019): carta de apresentação, modelo de ofício de
 * solicitação e ofício de anuência. A IA escreve só a carta e a justificativa, na hora (sem fila, como o follow-up da
 * Fase 9); sem IA, o kit sai dos modelos fixos. Nada é gravado aqui: a pessoa revisa, baixa e, se quiser, salva nos
 * documentos da licitação pelo envio de sempre.
 *
 * <p>Sem transação: a chamada ao modelo leva segundos e não deve segurar conexão do banco.
 */
@Service
public class CaronaKitAssistant {

    private static final Logger log = LoggerFactory.getLogger(CaronaKitAssistant.class);

    /** Itens citados no contexto do modelo; o kit em si leva todos os escolhidos. */
    static final int CONTEXT_ITEMS = 15;

    /**
     * O que dá para oferecer, antes de gerar o kit.
     *
     * @param companyMissing dados da empresa em branco no ambiente
     * @param aiEnabled se a IA está configurada (sem ela, o kit sai dos modelos fixos)
     */
    @Schema(name = "CaronaOfferResponse")
    public record OfferView(Offer offer, List<String> companyMissing, boolean aiEnabled) {}

    /**
     * @param to e-mails dos contatos do município, separados por vírgula
     * @param model quem escreveu; nulo sem IA
     */
    @Schema(name = "CaronaKitResponse")
    public record KitView(Kit kit, String to, String model) {}

    /** Uma carta pronta para baixar. */
    public record LetterFile(String fileName, byte[] content) {}

    /** Quanto o município vai pedir de um item; quantidade zero ou vazia deixa o item fora. */
    public record Line(UUID itemId, BigDecimal quantity) {}

    private final DocumentReader reader;
    private final CaronaOffers offers;
    private final AgencyDirectory agencies;
    private final CompanyProperties company;
    private final LetterDocuments letters;
    private final ExtractionMetrics metrics;
    private final Clock clock;

    CaronaKitAssistant(
            DocumentReader reader,
            CaronaOffers offers,
            AgencyDirectory agencies,
            CompanyProperties company,
            LetterDocuments letters,
            ExtractionMetrics metrics,
            Clock clock) {
        this.reader = reader;
        this.offers = offers;
        this.agencies = agencies;
        this.company = company;
        this.letters = letters;
        this.metrics = metrics;
        this.clock = clock;
    }

    public OfferView offer(UUID priceRecordId, UUID agencyId) {
        return new OfferView(
                offers.prepare(priceRecordId, agencyId), company.data().missing(), reader.enabled());
    }

    /**
     * Gera o kit.
     *
     * @param lines quanto pedir de cada item; nulo ou vazio usa o máximo de cada item
     * @param value ata sem itens: o valor da adesão; nulo usa o máximo
     * @param useAi pede a carta e a justificativa à IA (se ela estiver configurada)
     */
    public KitView kit(UUID priceRecordId, UUID agencyId, List<Line> lines, BigDecimal value, boolean useAi) {
        Offer offer = offers.prepare(priceRecordId, agencyId);
        List<String> warnings = new ArrayList<>();
        List<KitLine> chosen = lines(offer, lines, warnings);
        BigDecimal kitValue = null;
        if (offer.items().isEmpty()) {
            kitValue = value == null || value.signum() <= 0 ? offer.maxValue() : value;
            if (kitValue.compareTo(offer.maxValue()) > 0) {
                warnings.add(
                        "O valor passa do que o município pode aderir (" + BrFormat.money(offer.maxValue()) + ").");
            }
            if (kitValue.signum() <= 0) {
                throw new DomainRuleViolation("CARONA_NOTHING_TO_OFFER", "Não sobra valor para este município aderir.");
            }
        } else if (chosen.isEmpty()) {
            throw new DomainRuleViolation(
                    "CARONA_NOTHING_TO_OFFER", "Escolha a quantidade de pelo menos um item para o kit.");
        }

        String signature = CurrentUser.get().name();
        LocalDate today = LocalDate.now(clock);
        RawCaronaKit raw = null;
        String model = null;
        if (useAi) {
            if (!reader.enabled()) {
                throw new DomainRuleViolation(
                        "AI_DISABLED", "A IA não está configurada neste ambiente. Gere o kit sem IA.");
            }
            Answer<RawCaronaKit> answer = call(new CaronaKitRequest(context(offer, chosen, kitValue), signature));
            raw = answer.raw();
            model = answer.model();
        }
        Kit kit;
        try {
            kit = CaronaLetters.kit(offer, chosen, kitValue, company.data(), today, signature, raw);
        } catch (IllegalArgumentException e) {
            throw new DomainRuleViolation("AI_FAILED", "A IA não escreveu a carta. Tente de novo.");
        }
        if (!warnings.isEmpty()) {
            List<String> all = new ArrayList<>(warnings);
            all.addAll(kit.warnings());
            kit = new Kit(
                    kit.presentation(), kit.request(), kit.consent(), kit.total(), List.copyOf(all), kit.writtenByAi());
        }
        List<String> emails = agencies.contactEmails(agencyId);
        return new KitView(kit, emails.isEmpty() ? null : String.join(", ", emails), model);
    }

    /**
     * O .docx de uma carta, com o texto que a pessoa revisou.
     *
     * @param kind APRESENTACAO, SOLICITACAO ou ANUENCIA
     */
    public LetterFile docx(UUID priceRecordId, UUID agencyId, String kind, String text) {
        Offer offer = offers.prepare(priceRecordId, agencyId);
        if (text == null || text.isBlank()) {
            throw new DomainRuleViolation("REQUIRED", "A carta está vazia.");
        }
        String title = switch (kind) {
            case "APRESENTACAO" -> "Apresentação";
            case "SOLICITACAO" -> "Ofício de solicitação";
            case "ANUENCIA" -> "Anuência";
            default -> throw new DomainRuleViolation("INVALID_KIND", "Tipo de carta desconhecido.");
        };
        byte[] content = letters.docx(text, company.data(), !"SOLICITACAO".equals(kind));
        String name = title + " - " + offer.number() + " - " + offer.candidate().name();
        return new LetterFile(safeFileName(name) + ".docx", content);
    }

    /** Sem barra nem caractere que o Windows recusa no nome do arquivo. */
    static String safeFileName(String name) {
        String safe =
                name.replaceAll("[\\\\/:*?\"<>|]", "-").replaceAll("\\s+", " ").strip();
        return safe.length() > 150 ? safe.substring(0, 150).strip() : safe;
    }

    /** Linhas pedidas, na ordem da ata; sem pedido, o máximo de cada item com saldo. */
    private static List<KitLine> lines(Offer offer, List<Line> requested, List<String> warnings) {
        if (requested == null || requested.isEmpty()) {
            return offer.items().stream()
                    .filter(i -> i.maxQuantity().signum() > 0)
                    .map(i -> new KitLine(i, i.maxQuantity()))
                    .toList();
        }
        Map<UUID, BigDecimal> byItem = new LinkedHashMap<>();
        for (Line l : requested) {
            if (l.itemId() == null || l.quantity() == null || l.quantity().signum() <= 0) {
                continue;
            }
            byItem.merge(l.itemId(), l.quantity(), BigDecimal::add);
        }
        List<KitLine> result = new ArrayList<>();
        for (OfferItem item : offer.items()) {
            BigDecimal quantity = byItem.remove(item.itemId());
            if (quantity == null) {
                continue;
            }
            if (quantity.scale() > 3) {
                throw new DomainRuleViolation("INVALID_QUANTITY", "Use no máximo 3 casas decimais na quantidade.");
            }
            if (quantity.compareTo(item.maxQuantity()) > 0) {
                warnings.add("\"" + item.description() + "\": " + BrFormat.quantity(quantity)
                        + " passa do que o município pode aderir (" + BrFormat.quantity(item.maxQuantity())
                        + ").");
            }
            result.add(new KitLine(item, quantity));
        }
        if (!byItem.isEmpty()) {
            throw new DomainRuleViolation("ITEM_NOT_FOUND", "Item não encontrado nesta ata.");
        }
        return result;
    }

    private Answer<RawCaronaKit> call(CaronaKitRequest request) {
        try {
            Answer<RawCaronaKit> answer = reader.writeCaronaKit(request);
            metrics.finished("CARONA", Extraction.Status.DONE, answer.inputTokens(), answer.outputTokens());
            return answer;
        } catch (ReadingFailed e) {
            log.warn("IA (CARONA) falhou: {}", e.getMessage(), e.getCause());
            metrics.finished("CARONA", Extraction.Status.FAILED, e.inputTokens(), e.outputTokens());
            throw new DomainRuleViolation("AI_FAILED", e.getMessage());
        }
    }

    /** O que o modelo sabe: a ata, o que o município vai pedir, o município e o gerenciador. */
    private String context(Offer offer, List<KitLine> lines, BigDecimal value) {
        StringBuilder t = new StringBuilder();
        t.append("Hoje: ").append(BrFormat.date(LocalDate.now(clock))).append('\n');
        t.append("Ata de Registro de Preços nº ")
                .append(offer.number())
                .append(", da licitação ")
                .append(offer.tenderIdentification())
                .append('\n');
        t.append("Órgão gerenciador: ")
                .append(
                        offer.manager() == null
                                ? "não informado"
                                : offer.manager().name())
                .append('\n');
        t.append("Objeto: ").append(offer.object()).append('\n');
        t.append("Vigente até ")
                .append(BrFormat.date(offer.validUntil()))
                .append(" (faltam ")
                .append(offer.daysLeft())
                .append(" dias)\n");
        t.append("Município candidato: ")
                .append(offer.candidate().name())
                .append(
                        offer.candidate().city() == null
                                ? ""
                                : " (" + offer.candidate().city()
                                        + (offer.candidate().state() == null
                                                ? ""
                                                : "/" + offer.candidate().state()) + ")")
                .append('\n');
        t.append("Cada município pode aderir a até ")
                .append(offer.maxPerAgencyPercent())
                .append("% do registrado em cada item.\n");
        if (lines.isEmpty()) {
            t.append("A ata não tem itens cadastrados. Valor que o município pode aderir: ")
                    .append(BrFormat.money(value))
                    .append('\n');
        } else {
            t.append("\nO que o município pode pedir (quantidade, preço unitário, total):\n");
            lines.stream()
                    .limit(CONTEXT_ITEMS)
                    .forEach(l -> t.append("- ")
                            .append(l.item().description())
                            .append(": ")
                            .append(BrFormat.quantity(l.quantity()))
                            .append(
                                    l.item().unit() == null
                                            ? ""
                                            : " " + l.item().unit())
                            .append(", ")
                            .append(BrFormat.money(l.item().unitPrice()))
                            .append(", ")
                            .append(BrFormat.money(l.total()))
                            .append('\n'));
            if (lines.size() > CONTEXT_ITEMS) {
                t.append("(e mais ").append(lines.size() - CONTEXT_ITEMS).append(" itens)\n");
            }
            t.append("Valor total: ")
                    .append(BrFormat.money(lines.stream().map(KitLine::total).reduce(BigDecimal.ZERO, BigDecimal::add)))
                    .append('\n');
        }
        return t.toString();
    }
}
