package br.com.shema.crm.proposals.infrastructure;

import br.com.shema.crm.proposals.application.ProposalDocuments;
import br.com.shema.crm.proposals.domain.Proposal;
import br.com.shema.crm.proposals.domain.ProposalItem;
import br.com.shema.crm.shared.company.CompanyData;
import br.com.shema.crm.shared.domain.AmountInWords;
import br.com.shema.crm.shared.domain.BrDocuments;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * PDF A4 da proposta com OpenPDF: cabeçalho da empresa, número, cliente, itens, total por extenso, condições, data
 * por extenso e a linha de assinatura em branco com nome, cargo e CPF. Não assina: quem baixa assina no assinador.
 * As colunas de unidade e marca só aparecem se algum item tiver.
 *
 * <p>Com o papel timbrado ({@code shema.proposals.letterhead}), a imagem A4 vai no fundo de cada página, o texto fica
 * entre o logo e a faixa do rodapé e o cabeçalho em texto não entra (o timbrado já tem razão social, CNPJ e endereço).
 */
@Component
class OpenPdfProposalDocuments implements ProposalDocuments {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", PT_BR);

    private static final Color TEXT = new Color(0x1F, 0x29, 0x37);
    private static final Color MUTED = new Color(0x4B, 0x55, 0x63);
    private static final Color LINE = new Color(0xD1, 0xD5, 0xDB);
    private static final Color HEADER_FILL = new Color(0xEE, 0xF2, 0xF7);

    private static final Font COMPANY = new Font(Font.HELVETICA, 13, Font.BOLD, TEXT);
    private static final Font SMALL = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, MUTED);
    private static final Font TITLE = new Font(Font.HELVETICA, 14, Font.BOLD, TEXT);
    private static final Font SUBTITLE = new Font(Font.HELVETICA, 10, Font.NORMAL, MUTED);
    private static final Font SECTION = new Font(Font.HELVETICA, 10, Font.BOLD, TEXT);
    private static final Font LABEL = new Font(Font.HELVETICA, 9, Font.BOLD, TEXT);
    private static final Font BODY = new Font(Font.HELVETICA, 9, Font.NORMAL, TEXT);
    private static final Font BODY_BOLD = new Font(Font.HELVETICA, 9, Font.BOLD, TEXT);
    private static final Font FOOTER = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, MUTED);

    private static final Logger log = LoggerFactory.getLogger(OpenPdfProposalDocuments.class);

    /** 2 cm em pontos. */
    private static final float MARGIN = 56.7f;

    /** Com o timbrado: abaixo do logo (que termina a ~95 pt do topo) e acima da faixa do rodapé (~120 pt da base). */
    private static final float LETTERHEAD_TOP = 120f;

    private static final float LETTERHEAD_BOTTOM = 140f;

    /** A imagem do timbrado, ou null para o cabeçalho em texto. */
    private final byte[] letterhead;

    OpenPdfProposalDocuments(
            ResourceLoader resources,
            @Value("${shema.proposals.letterhead:classpath:branding/timbrado-shema.png}") String letterheadLocation) {
        this.letterhead = load(resources, letterheadLocation);
    }

    private static byte[] load(ResourceLoader resources, String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        Resource resource = resources.getResource(location.trim());
        if (!resource.exists()) {
            log.warn("Timbrado da proposta não encontrado em {}: o PDF sai com cabeçalho em texto", location);
            return null;
        }
        try {
            byte[] bytes = resource.getContentAsByteArray();
            Image.getInstance(bytes); // confere que é uma imagem antes da primeira proposta
            return bytes;
        } catch (IOException | RuntimeException e) {
            log.warn("Timbrado da proposta inválido em {}: o PDF sai com cabeçalho em texto", location, e);
            return null;
        }
    }

    @Override
    public boolean usesLetterhead() {
        return letterhead != null;
    }

    @Override
    public byte[] pdf(Proposal proposal, CompanyData company) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Image background = letterheadImage();
        Document doc = background == null
                ? new Document(PageSize.A4, MARGIN, MARGIN, MARGIN, MARGIN)
                : new Document(PageSize.A4, MARGIN, MARGIN, LETTERHEAD_TOP, LETTERHEAD_BOTTOM);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        writer.setPageEvent(
                new PageDecoration("Proposta nº " + proposal.number().text(), background));
        doc.addTitle("Proposta de preços nº " + proposal.number().text());
        doc.addAuthor(company.legalNameOr());
        doc.open();
        if (background == null) {
            header(doc, company);
        }
        title(doc, proposal);
        client(doc, proposal);
        items(doc, proposal);
        conditions(doc, proposal);
        signature(doc, proposal, company);
        doc.close();
        return out.toByteArray();
    }

    /** Uma instância por documento: o PDF guarda a imagem uma vez só e repete nas páginas. */
    private Image letterheadImage() {
        if (letterhead == null) {
            return null;
        }
        try {
            Image image = Image.getInstance(letterhead);
            image.scaleAbsolute(PageSize.A4.getWidth(), PageSize.A4.getHeight());
            image.setAbsolutePosition(0, 0);
            return image;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void header(Document doc, CompanyData company) {
        Paragraph name = new Paragraph(company.legalNameOr(), COMPANY);
        doc.add(name);
        List<String> lines = new ArrayList<>();
        lines.add("CNPJ "
                + (company.cnpj() == null || company.cnpj().isBlank()
                        ? company.cnpjOr()
                        : BrDocuments.formatCnpj(company.cnpj().replaceAll("[\\s./\\-]", ""))));
        String address = join(" · ", company.address(), company.city());
        if (!address.isEmpty()) {
            lines.add(address);
        }
        String contact = company.contactLine();
        if (!contact.isEmpty()) {
            lines.add(contact);
        }
        for (String line : lines) {
            doc.add(new Paragraph(line, SMALL));
        }
        rule(doc, 6);
    }

    private static void title(Document doc, Proposal proposal) {
        Paragraph title =
                new Paragraph("PROPOSTA DE PREÇOS Nº " + proposal.number().text(), TITLE);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(8);
        doc.add(title);
        Paragraph kind = new Paragraph(proposal.getKind().title(), SUBTITLE);
        kind.setAlignment(Element.ALIGN_CENTER);
        kind.setSpacingAfter(12);
        doc.add(kind);
    }

    private static void client(Document doc, Proposal p) {
        section(doc, "Dados do cliente");
        PdfPTable table = new PdfPTable(new float[] {1.3f, 5f});
        table.setWidthPercentage(100);
        labelValue(table, "Cliente", p.getClientName());
        labelValue(table, "CNPJ", BrDocuments.formatCnpj(p.getClientCnpj()));
        labelValue(table, "Endereço", p.getClientAddress());
        labelValue(table, "E-mail", p.getClientEmail());
        labelValue(table, "Telefones", p.getClientPhones());
        table.setSpacingAfter(12);
        doc.add(table);
    }

    private static void items(Document doc, Proposal p) {
        section(doc, "Itens");
        List<ProposalItem> items = p.getItems();
        boolean hasUnit = items.stream().anyMatch(i -> i.getUnit() != null);
        boolean hasBrand = items.stream().anyMatch(i -> i.getBrand() != null);
        List<Float> widths = new ArrayList<>(List.of(0.7f, 5f));
        List<String> headers = new ArrayList<>(List.of("Item", "Descrição"));
        if (hasUnit) {
            widths.add(0.9f);
            headers.add("Unid.");
        }
        if (hasBrand) {
            widths.add(1.5f);
            headers.add("Marca");
        }
        widths.addAll(List.of(1.1f, 1.6f, 1.7f));
        headers.addAll(List.of("Qtd.", "Valor unit.", "Valor total"));
        float[] w = new float[widths.size()];
        for (int i = 0; i < w.length; i++) {
            w[i] = widths.get(i);
        }
        PdfPTable table = new PdfPTable(w);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (int i = 0; i < headers.size(); i++) {
            PdfPCell cell =
                    cell(headers.get(i), LABEL, i >= headers.size() - 3 ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            cell.setBackgroundColor(HEADER_FILL);
            table.addCell(cell);
        }
        for (ProposalItem item : items) {
            table.addCell(cell(item.displayNumber(), BODY, Element.ALIGN_LEFT));
            table.addCell(cell(item.getDescription(), BODY, Element.ALIGN_LEFT));
            if (hasUnit) {
                table.addCell(cell(item.getUnit(), BODY, Element.ALIGN_LEFT));
            }
            if (hasBrand) {
                table.addCell(cell(item.getBrand(), BODY, Element.ALIGN_LEFT));
            }
            table.addCell(cell(quantity(item.getQuantity()), BODY, Element.ALIGN_RIGHT));
            table.addCell(cell(money(item.getUnitPrice()), BODY, Element.ALIGN_RIGHT));
            table.addCell(cell(money(item.total()), BODY, Element.ALIGN_RIGHT));
        }
        PdfPCell totalLabel = cell("Valor total da proposta", BODY_BOLD, Element.ALIGN_RIGHT);
        totalLabel.setColspan(headers.size() - 1);
        totalLabel.setBackgroundColor(HEADER_FILL);
        table.addCell(totalLabel);
        PdfPCell totalValue = cell(money(p.getTotal()), BODY_BOLD, Element.ALIGN_RIGHT);
        totalValue.setBackgroundColor(HEADER_FILL);
        table.addCell(totalValue);
        doc.add(table);
        Paragraph words = new Paragraph(
                "Valor total: " + money(p.getTotal()) + " (" + AmountInWords.reais(p.getTotal()) + ").", BODY);
        words.setSpacingBefore(6);
        words.setSpacingAfter(12);
        doc.add(words);
    }

    private static void conditions(Document doc, Proposal p) {
        section(doc, "Condições");
        PdfPTable table = new PdfPTable(new float[] {1.8f, 4.5f});
        table.setWidthPercentage(100);
        int days = p.getValidityDays();
        labelValue(table, "Validade da proposta", days + (days == 1 ? " dia" : " dias") + " a contar da data abaixo");
        labelValue(table, "Forma de pagamento", p.getPaymentTerms());
        labelValue(table, "Prazo de entrega", p.getDeliveryTerms());
        table.setSpacingAfter(18);
        doc.add(table);
    }

    /** Data, espaço para a assinatura e quem assina, numa tabela sem bordas que não se divide entre páginas. */
    private static void signature(Document doc, Proposal p, CompanyData company) {
        String date = p.getProposalDate().format(LONG_DATE);
        String place = company.city() == null || company.city().isBlank()
                ? date
                : company.city().trim() + ", " + date;
        PdfPTable block = new PdfPTable(1);
        block.setWidthPercentage(100);
        block.setKeepTogether(true);
        Paragraph when = new Paragraph(place + ".", BODY);
        block.addCell(plain(when, Element.ALIGN_LEFT, 48));
        // Espaço em branco acima da linha: o assinador põe a assinatura aqui
        Paragraph line = new Paragraph(new Chunk(new LineSeparator(0.6f, 45, MUTED, Element.ALIGN_CENTER, 0)));
        block.addCell(plain(line, Element.ALIGN_CENTER, 2));
        block.addCell(plain(new Paragraph(p.getSignerName(), BODY_BOLD), Element.ALIGN_CENTER, 0));
        block.addCell(plain(new Paragraph(p.getSignerRole(), BODY), Element.ALIGN_CENTER, 0));
        block.addCell(
                plain(new Paragraph("CPF " + BrDocuments.formatCpf(p.getSignerCpf()), BODY), Element.ALIGN_CENTER, 0));
        block.addCell(plain(new Paragraph(company.legalNameOr(), BODY), Element.ALIGN_CENTER, 0));
        doc.add(block);
    }

    private static PdfPCell plain(Paragraph content, int align, float paddingBottom) {
        content.setAlignment(align);
        PdfPCell cell = new PdfPCell();
        cell.addElement(content);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        cell.setPaddingBottom(paddingBottom);
        return cell;
    }

    private static void section(Document doc, String text) {
        Paragraph section = new Paragraph(text.toUpperCase(PT_BR), SECTION);
        section.setSpacingAfter(4);
        doc.add(section);
    }

    private static void rule(Document doc, float after) {
        Paragraph line = new Paragraph(new Chunk(new LineSeparator(0.6f, 100, LINE, Element.ALIGN_CENTER, -2)));
        line.setSpacingAfter(after);
        doc.add(line);
    }

    private static void labelValue(PdfPTable table, String label, String value) {
        table.addCell(cell(label, LABEL, Element.ALIGN_LEFT));
        table.addCell(cell(value == null || value.isBlank() ? "—" : value, BODY, Element.ALIGN_LEFT));
    }

    private static PdfPCell cell(String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(4);
        cell.setPaddingBottom(5);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(LINE);
        cell.setBorderWidth(0.5f);
        // Fundo branco: os arcos do timbrado não passam por trás dos valores
        cell.setBackgroundColor(Color.WHITE);
        return cell;
    }

    /** R$ com 2 casas, ou até 4 quando o valor unitário tem mais. */
    private static String money(BigDecimal value) {
        if (value == null) {
            return "";
        }
        NumberFormat format = NumberFormat.getNumberInstance(PT_BR);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(4);
        return "R$ " + format.format(value);
    }

    private static String quantity(BigDecimal value) {
        if (value == null) {
            return "";
        }
        NumberFormat format = NumberFormat.getNumberInstance(PT_BR);
        format.setMaximumFractionDigits(3);
        return format.format(value);
    }

    private static String join(String separator, String... parts) {
        List<String> present = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                present.add(part.trim());
            }
        }
        return String.join(separator, present);
    }

    /** Em cada página: o timbrado no fundo, se houver, e o rodapé com o número da proposta e da página. */
    private static final class PageDecoration extends PdfPageEventHelper {

        private final String label;
        private final Image background;

        PageDecoration(String label, Image background) {
            this.label = label;
            this.background = background;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            if (background != null) {
                writer.getDirectContentUnder().addImage(background);
            }
            Phrase text = new Phrase(label + " · página " + writer.getPageNumber(), FOOTER);
            ColumnText.showTextAligned(
                    writer.getDirectContent(),
                    Element.ALIGN_CENTER,
                    text,
                    (document.left() + document.right()) / 2,
                    document.bottom() - (background == null ? 24 : 14),
                    0);
        }
    }
}
