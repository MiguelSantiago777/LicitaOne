package br.com.shema.crm.extraction.domain;

import br.com.shema.crm.pricerecords.CaronaOffers.Offer;
import br.com.shema.crm.pricerecords.CaronaOffers.OfferItem;
import br.com.shema.crm.shared.domain.BrFormat;
import br.com.shema.crm.tenders.AgencyRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Kit de carona (Fase 10, ADR 0019): carta de apresentação, modelo de ofício de solicitação e ofício de anuência.
 *
 * <p>A estrutura e a base legal ficam aqui, em modelos fixos: a IA escreve só a carta de apresentação e a
 * justificativa do ofício. A anuência é sempre o modelo fixo, porque é o aceite da empresa e não deve variar.
 */
public final class CaronaLetters {

    /** Cabe numa página de ofício com folga; acima disso, corta e avisa. */
    public static final int MAX_TEXT = 6000;

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", PT_BR);

    /** Citação de lei, artigo ou decreto com número: a base legal é do modelo fixo, não do texto da IA. */
    private static final Pattern LAW = Pattern.compile("(?iu)\\b(lei|art\\.?|artigo|decreto)\\s*(n[º°o.]*\\s*)?\\d");

    private CaronaLetters() {}

    /** Uma linha do kit: o item da ata e quanto o município vai pedir. */
    public record KitLine(OfferItem item, BigDecimal quantity) {

        public BigDecimal total() {
            return quantity.multiply(item.unitPrice()).setScale(2, RoundingMode.HALF_UP);
        }
    }

    /**
     * @param kind APRESENTACAO, SOLICITACAO ou ANUENCIA
     * @param subject assunto do e-mail; só na apresentação
     * @param letterhead se o .docx leva o cabeçalho da empresa (a solicitação é do município, não leva)
     */
    @Schema(name = "CaronaLetterResponse")
    public record Letter(String kind, String title, String subject, String body, boolean letterhead) {}

    /**
     * @param total valor do que o kit oferece
     * @param writtenByAi se a apresentação e a justificativa vieram da IA (sem IA, os modelos fixos)
     */
    @Schema(name = "CaronaKitTextsResponse")
    public record Kit(
            Letter presentation,
            Letter request,
            Letter consent,
            BigDecimal total,
            List<String> warnings,
            boolean writtenByAi) {}

    /**
     * Monta o kit. Sem {@code raw} (IA desligada ou não pedida), a apresentação e a justificativa são os modelos
     * fixos.
     *
     * @param value ata sem itens: o valor que o município vai aderir; nulo quando há linhas
     * @param signature quem está mandando
     */
    public static Kit kit(
            Offer offer,
            List<KitLine> lines,
            BigDecimal value,
            CompanyData company,
            LocalDate today,
            String signature,
            RawCaronaKit raw) {
        List<String> warnings = new ArrayList<>();
        BigDecimal total = lines.isEmpty()
                ? (value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP))
                : lines.stream().map(KitLine::total).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);

        String subject;
        String presentation;
        String justification;
        if (raw == null) {
            subject = "Adesão à Ata de Registro de Preços nº " + offer.number();
            presentation = fixedPresentation(offer, lines, total, company, signature);
            justification = "[Descreva por que a adesão é vantajosa para o município: necessidade, preço já"
                    + " licitado, prazo.]";
        } else {
            if (raw.warnings() != null) {
                raw.warnings().stream()
                        .filter(w -> w != null && !w.isBlank())
                        .map(String::trim)
                        .forEach(warnings::add);
            }
            subject = ReadingResult.blankToNull(raw.presentationSubject(), 200);
            if (subject == null) {
                subject = "Adesão à Ata de Registro de Preços nº " + offer.number();
            }
            presentation =
                    raw.presentationBody() == null ? "" : raw.presentationBody().strip();
            if (presentation.isEmpty()) {
                throw new IllegalArgumentException("A IA não escreveu a carta.");
            }
            presentation = cut(presentation, warnings, "A carta de apresentação");
            justification = raw.requestJustification() == null
                    ? ""
                    : raw.requestJustification().strip();
            if (justification.isEmpty()) {
                justification = "[Descreva por que a adesão é vantajosa para o município.]";
                warnings.add("A IA não escreveu a justificativa do ofício. Preencha antes de mandar.");
            }
            justification = cut(justification, warnings, "A justificativa");
            if (LAW.matcher(presentation).find() || LAW.matcher(justification).find()) {
                warnings.add("O texto da IA cita lei ou artigo. Confira a citação ou apague: a base legal já está"
                        + " no ofício.");
            }
        }
        if (!company.missing().isEmpty()) {
            warnings.add("Faltam dados da empresa (" + String.join(", ", company.missing())
                    + "): os textos mostram o campo entre colchetes. Configure SHEMA_COMPANY_* no ambiente.");
        }
        return new Kit(
                new Letter("APRESENTACAO", "Carta de apresentação", subject, presentation, true),
                new Letter(
                        "SOLICITACAO",
                        "Modelo de ofício de solicitação",
                        null,
                        request(offer, lines, total, company, justification, today),
                        false),
                new Letter("ANUENCIA", "Ofício de anuência", null, consent(offer, lines, total, company, today), true),
                total,
                List.copyOf(warnings),
                raw != null);
    }

    /** Ofício do município ao gerenciador, para ele adaptar: base legal fixa, campos dele entre colchetes. */
    static String request(
            Offer offer,
            List<KitLine> lines,
            BigDecimal total,
            CompanyData company,
            String justification,
            LocalDate today) {
        AgencyRef candidate = offer.candidate();
        StringBuilder t = new StringBuilder();
        t.append("Ofício nº [número]/").append(today.getYear()).append(" – [Secretaria solicitante]\n\n");
        t.append(place(candidate)).append(", [data].\n\n");
        t.append("Ao(À) ").append(managerName(offer)).append('\n');
        t.append("Órgão gerenciador da Ata de Registro de Preços nº ")
                .append(offer.number())
                .append("\n\n");
        t.append("Assunto: solicitação de adesão à Ata de Registro de Preços nº ")
                .append(offer.number())
                .append("\n\n");
        t.append("Senhor(a) Gestor(a),\n\n");
        t.append("O(A) ")
                .append(candidate.name())
                .append(", por meio da [Secretaria solicitante], solicita autorização para aderir, na condição de"
                        + " órgão não participante, à Ata de Registro de Preços nº ")
                .append(offer.number())
                .append(", decorrente da licitação ")
                .append(offer.tenderIdentification())
                .append(", cujo objeto é ")
                .append(lowerFirst(offer.object()))
                .append(", vigente até ")
                .append(BrFormat.date(offer.validUntil()))
                .append(", nos termos do art. 86, § 2º, da Lei nº 14.133/2021.\n\n");
        items(t, lines, total, "Itens e quantidades pretendidos:");
        t.append("Justificativa da vantagem da adesão:\n").append(justification).append("\n\n");
        t.append("A compatibilidade dos preços registrados com os praticados no mercado está demonstrada na pesquisa"
                + " de preços do processo administrativo nº [número do processo]. A despesa correrá pela dotação"
                + " orçamentária [dotação].\n\n");
        t.append("O fornecedor registrado, ")
                .append(company.legalNameOr())
                .append(", CNPJ ")
                .append(company.cnpjOr())
                .append(", manifestou concordância com o fornecimento nas condições da ata, conforme ofício de"
                        + " anuência anexo.\n\n");
        t.append("Atenciosamente,\n\n[Nome da autoridade]\n[Cargo]\n").append(candidate.name());
        return t.toString();
    }

    /** Aceite da empresa: sempre este texto, com os dados da ata e as quantidades. */
    static String consent(Offer offer, List<KitLine> lines, BigDecimal total, CompanyData company, LocalDate today) {
        AgencyRef candidate = offer.candidate();
        StringBuilder t = new StringBuilder();
        t.append("OFÍCIO DE ANUÊNCIA\n\n");
        t.append(company.cityOr()).append(", ").append(today.format(LONG_DATE)).append(".\n\n");
        t.append("Ao(À) ").append(candidate.name()).append('\n');
        t.append("Com cópia ao(à) ").append(managerName(offer)).append(", órgão gerenciador\n\n");
        t.append("Assunto: anuência à adesão à Ata de Registro de Preços nº ")
                .append(offer.number())
                .append("\n\n");
        t.append(company.legalNameOr())
                .append(", inscrita no CNPJ sob o nº ")
                .append(company.cnpjOr())
                .append(", com sede em ")
                .append(company.addressOr())
                .append(", fornecedora registrada na Ata de Registro de Preços nº ")
                .append(offer.number())
                .append(", decorrente da licitação ")
                .append(offer.tenderIdentification())
                .append(" do(a) ")
                .append(managerName(offer))
                .append(", declara que concorda com a adesão do(a) ")
                .append(candidate.name())
                .append(" à referida ata e se compromete a fornecer os itens abaixo nos mesmos preços e condições"
                        + " registrados, durante a vigência da ata (até ")
                .append(BrFormat.date(offer.validUntil()))
                .append("), sem prejuízo das obrigações assumidas com o órgão gerenciador e os participantes.\n\n");
        items(t, lines, total, "Itens e quantidades:");
        t.append("Esta anuência vale para a adesão autorizada pelo órgão gerenciador, dentro dos limites de"
                + " quantidade da ata.\n\n");
        t.append("Atenciosamente,\n\n")
                .append(company.representativeOr())
                .append('\n')
                .append(company.representativeRoleOr())
                .append('\n')
                .append(company.legalNameOr());
        return t.toString();
    }

    /** Apresentação sem IA: o mesmo conteúdo da carta escrita pela IA, em texto fixo. */
    static String fixedPresentation(
            Offer offer, List<KitLine> lines, BigDecimal total, CompanyData company, String signature) {
        StringBuilder t = new StringBuilder();
        t.append("Prezados(as),\n\n");
        t.append("A ")
                .append(company.legalNameOr())
                .append(" é fornecedora registrada na Ata de Registro de Preços nº ")
                .append(offer.number())
                .append(" do(a) ")
                .append(managerName(offer))
                .append(", para ")
                .append(lowerFirst(offer.object()))
                .append(". A ata vale até ")
                .append(BrFormat.date(offer.validUntil()))
                .append(" e permite a adesão de outros órgãos.\n\n");
        t.append("Com a adesão, o(a) ")
                .append(offer.candidate().name())
                .append(" contrata pelos preços já licitados, sem abrir uma nova licitação.\n\n");
        items(t, lines, total, "O que o município pode pedir pela ata:");
        t.append("Como funciona:\n");
        t.append("1. O município envia ao órgão gerenciador o ofício pedindo a adesão (modelo anexo).\n");
        t.append("2. O gerenciador autoriza.\n");
        t.append("3. A ").append(company.legalNameOr()).append(" confirma o fornecimento (anuência anexa).\n");
        t.append("4. O município junta a pesquisa de preços e a justificativa e faz a contratação.\n\n");
        t.append("Ficamos à disposição para ajudar no processo.\n\nAtenciosamente,\n")
                .append(signature)
                .append('\n')
                .append(company.legalNameOr());
        String contact = company.contactLine();
        if (!contact.isEmpty()) {
            t.append('\n').append(contact);
        }
        return t.toString();
    }

    private static void items(StringBuilder t, List<KitLine> lines, BigDecimal total, String heading) {
        t.append(heading).append('\n');
        if (lines.isEmpty()) {
            t.append("- Fornecimento conforme a ata, até ")
                    .append(BrFormat.money(total))
                    .append("\n\n");
            return;
        }
        for (KitLine l : lines) {
            OfferItem i = l.item();
            t.append("- ")
                    .append(
                            i.lot() == null || i.lot().isBlank()
                                    ? ""
                                    : "Lote " + i.lot().trim() + " · ")
                    .append(i.description())
                    .append(": ")
                    .append(BrFormat.quantity(l.quantity()))
                    .append(
                            i.unit() == null || i.unit().isBlank()
                                    ? ""
                                    : " " + i.unit().trim())
                    .append(" × ")
                    .append(BrFormat.money(i.unitPrice()))
                    .append(" = ")
                    .append(BrFormat.money(l.total()))
                    .append('\n');
        }
        t.append("Valor total: ").append(BrFormat.money(total)).append("\n\n");
    }

    private static String cut(String text, List<String> warnings, String what) {
        if (text.length() <= MAX_TEXT) {
            return text;
        }
        warnings.add(what + " foi cortada em 6.000 caracteres.");
        return text.substring(0, MAX_TEXT);
    }

    private static String managerName(Offer offer) {
        return offer.manager() == null ? "[órgão gerenciador]" : offer.manager().name();
    }

    private static String place(AgencyRef agency) {
        if (agency.city() == null || agency.city().isBlank()) {
            return "[Cidade]";
        }
        return agency.state() == null || agency.state().isBlank()
                ? agency.city()
                : agency.city() + "/" + agency.state();
    }

    private static String lowerFirst(String text) {
        if (text == null || text.isBlank()) {
            return "[objeto]";
        }
        String t = text.strip();
        // Sigla ("SRP", "TI") fica como está
        if (t.length() > 1 && Character.isUpperCase(t.charAt(1))) {
            return t;
        }
        return Character.toLowerCase(t.charAt(0)) + t.substring(1);
    }
}
