package br.com.shema.crm.reports.infrastructure;

import br.com.shema.crm.reports.application.ReportDocuments;
import br.com.shema.crm.reports.application.ReportView;
import br.com.shema.crm.reports.application.ReportView.MonthRow;
import br.com.shema.crm.reports.application.ReportView.Slice;
import br.com.shema.crm.reports.application.ReportView.Summary;
import br.com.shema.crm.reports.application.ReportView.YearRow;
import br.com.shema.crm.shared.TimeConfig;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * PDF A4 do relatório com OpenPDF: cartões, colunas mês a mês (o ano em azul, o anterior em cinza), acumulado, tabela
 * mês a mês, anos e as quebras em barras. Os gráficos são desenhados direto no PDF (vetor, sem biblioteca de
 * gráficos) com as mesmas cores e regras da tela. Usa o mesmo papel timbrado das propostas.
 */
@Component
class OpenPdfReportDocuments implements ReportDocuments {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter GENERATED =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", PT_BR).withZone(TimeConfig.BUSINESS_ZONE);

    private static final Color TEXT = new Color(0x0F, 0x17, 0x2A);
    private static final Color MUTED = new Color(0x47, 0x55, 0x69);
    private static final Color GRID = new Color(0xE5, 0xE9, 0xF0);
    private static final Color HEADER_FILL = new Color(0xF1, 0xF5, 0xF9);
    /** A série do ano (azul do sistema) e a de comparação (cinza de ênfase), as mesmas da tela. */
    private static final Color SERIES = new Color(0x25, 0x63, 0xEB);

    private static final Color COMPARISON = new Color(0x94, 0xA3, 0xB8);

    private static final Font TITLE = new Font(Font.HELVETICA, 15, Font.BOLD, TEXT);
    private static final Font SUBTITLE = new Font(Font.HELVETICA, 9.5f, Font.NORMAL, MUTED);
    private static final Font SECTION = new Font(Font.HELVETICA, 11, Font.BOLD, TEXT);
    private static final Font LABEL = new Font(Font.HELVETICA, 8, Font.NORMAL, MUTED);
    private static final Font VALUE = new Font(Font.HELVETICA, 12, Font.BOLD, TEXT);
    private static final Font BODY = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, TEXT);
    private static final Font BODY_BOLD = new Font(Font.HELVETICA, 8.5f, Font.BOLD, TEXT);
    private static final Font SMALL = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, TEXT);
    private static final Font FOOTER = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, MUTED);

    private static final float MARGIN = 48f;
    private static final float LETTERHEAD_TOP = 120f;
    private static final float LETTERHEAD_BOTTOM = 140f;

    private static final Logger log = LoggerFactory.getLogger(OpenPdfReportDocuments.class);

    private final byte[] letterhead;
    private final BaseFont axisFont;

    OpenPdfReportDocuments(
            ResourceLoader resources,
            @Value("${shema.proposals.letterhead:classpath:branding/timbrado-shema.png}") String letterheadLocation) {
        this.letterhead = load(resources, letterheadLocation);
        try {
            this.axisFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, false);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] load(ResourceLoader resources, String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        Resource resource = resources.getResource(location.trim());
        if (!resource.exists()) {
            log.warn("Timbrado não encontrado em {}: o relatório sai sem ele", location);
            return null;
        }
        try {
            byte[] bytes = resource.getContentAsByteArray();
            Image.getInstance(bytes);
            return bytes;
        } catch (IOException | RuntimeException e) {
            log.warn("Timbrado inválido em {}: o relatório sai sem ele", location, e);
            return null;
        }
    }

    @Override
    public byte[] pdf(ReportView r) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Image background = letterheadImage();
        Document doc = background == null
                ? new Document(PageSize.A4, MARGIN, MARGIN, MARGIN, MARGIN)
                : new Document(PageSize.A4, MARGIN, MARGIN, LETTERHEAD_TOP, LETTERHEAD_BOTTOM);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        String label = r.metricLabel() + " · " + r.year();
        writer.setPageEvent(new PageDecoration(label, background));
        doc.addTitle("Relatório · " + label);
        doc.open();

        Paragraph title = new Paragraph(r.metricLabel() + " em " + r.year(), TITLE);
        doc.add(title);
        Paragraph sub = new Paragraph(subtitle(r), SUBTITLE);
        sub.setSpacingAfter(10);
        doc.add(sub);

        cards(doc, r);

        section(doc, "Mês a mês");
        doc.add(legend(writer, r.year(), r.year() - 1));
        doc.add(monthsChart(writer, r, doc.right() - doc.left()));
        doc.add(monthsTable(r));

        section(doc, "Acumulado do ano");
        doc.add(legend(writer, r.year(), r.year() - 1));
        doc.add(cumulativeChart(writer, r, doc.right() - doc.left()));

        if (!r.years().isEmpty()) {
            section(doc, "Comparação entre anos");
            doc.add(yearsTable(r.years()));
        }
        slices(doc, writer, "Órgãos que mais compraram", r.byAgency());
        slices(doc, writer, "Municípios", r.byCity());
        slices(doc, writer, "Por UF", r.byState());
        if (r.kindTitle() != null) {
            slices(doc, writer, r.kindTitle(), r.byKind());
        }
        doc.close();
        return out.toByteArray();
    }

    private static String subtitle(ReportView r) {
        StringBuilder s = new StringBuilder(r.dateBasis() + " no mês");
        if (r.state() != null) {
            s.append(" · UF ").append(r.state());
        }
        if (r.sphereLabel() != null) {
            s.append(" · esfera ").append(r.sphereLabel().toLowerCase(PT_BR));
        }
        s.append(" · gerado em ").append(GENERATED.format(r.generatedAt()));
        return s.toString();
    }

    // ---- cartões ----

    private static void cards(Document doc, ReportView r) {
        Summary s = r.summary();
        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setSpacingAfter(6);
        card(t, "Total no ano", money(s.total()), s.count() + " " + (s.count() == 1 ? r.singular() : r.plural()));
        card(
                t,
                "Média por mês",
                money(s.average()),
                r.elapsedMonths() == 12 ? "nos 12 meses" : "em " + r.elapsedMonths() + " meses");
        card(
                t,
                "Melhor mês",
                s.bestMonth() == null ? "—" : monthName(s.bestMonth()),
                s.bestValue() == null ? "sem movimento" : money(s.bestValue()));
        String change = s.changePercent() == null
                ? "sem base"
                : (s.changePercent().signum() > 0 ? "+" : "") + percent(s.changePercent());
        card(
                t,
                "Sobre " + (r.year() - 1),
                change,
                r.elapsedMonths() == 0
                        ? "ano ainda não começou"
                        : "jan–" + monthShort(r.elapsedMonths()).toLowerCase(PT_BR) + " de " + (r.year() - 1) + ": "
                                + money(s.previousSamePeriod()));
        doc.add(t);
        if (s.worstMonth() != null) {
            Paragraph worst = new Paragraph(
                    "Mês mais fraco com movimento: " + monthName(s.worstMonth()) + " (" + money(s.worstValue()) + ").",
                    LABEL);
            worst.setSpacingAfter(4);
            doc.add(worst);
        }
    }

    private static void card(PdfPTable t, String label, String value, String detail) {
        PdfPCell c = new PdfPCell();
        c.setBorderColor(GRID);
        c.setPadding(7);
        c.addElement(new Paragraph(label, LABEL));
        c.addElement(new Paragraph(value, VALUE));
        c.addElement(new Paragraph(detail, LABEL));
        t.addCell(c);
    }

    // ---- gráficos ----

    private Image legend(PdfWriter writer, int year, int previous) {
        PdfTemplate tpl = writer.getDirectContent().createTemplate(200, 14);
        swatch(tpl, 0, SERIES, String.valueOf(year));
        swatch(tpl, 60, COMPARISON, String.valueOf(previous));
        return image(tpl);
    }

    private void swatch(PdfTemplate tpl, float x, Color color, String label) {
        tpl.setColorFill(color);
        tpl.roundRectangle(x, 3, 8, 8, 2);
        tpl.fill();
        text(tpl, label, x + 12, 4, 8, MUTED, Element.ALIGN_LEFT);
    }

    /** Colunas mês a mês: o ano anterior em cinza ao lado, o valor só no melhor mês. */
    private Image monthsChart(PdfWriter writer, ReportView r, float width) {
        float height = 190;
        float left = 52;
        float bottom = 18;
        float top = 14;
        PdfTemplate tpl = writer.getDirectContent().createTemplate(width, height);
        double max = 0;
        for (MonthRow m : r.months()) {
            max = Math.max(
                    max, Math.max(m.value().doubleValue(), m.previousYearValue().doubleValue()));
        }
        double scale = niceMax(max);
        float plotH = height - bottom - top;
        float plotW = width - left;
        grid(tpl, left, bottom, plotW, plotH, scale);
        float slot = plotW / 12f;
        float bar = Math.min(12, (slot - 8) / 2f);
        Integer best = r.summary().bestMonth();
        for (MonthRow m : r.months()) {
            float cx = left + slot * (m.month() - 1) + slot / 2f;
            column(
                    tpl,
                    cx - bar - 1,
                    bottom,
                    bar,
                    (float) (m.previousYearValue().doubleValue() / scale * plotH),
                    COMPARISON);
            float h = (float) (m.value().doubleValue() / scale * plotH);
            column(tpl, cx + 1, bottom, bar, h, SERIES);
            text(tpl, monthShort(m.month()), cx, 5, 7.5f, MUTED, Element.ALIGN_CENTER);
            if (best != null && best == m.month()) {
                text(tpl, compact(m.value()), cx + 1 + bar / 2, bottom + h + 3, 7.5f, TEXT, Element.ALIGN_CENTER);
            }
        }
        return image(tpl);
    }

    /** Acumulado: duas linhas de 2 pt; a do ano para no último mês com dado. */
    private Image cumulativeChart(PdfWriter writer, ReportView r, float width) {
        float height = 150;
        float left = 52;
        float bottom = 18;
        float top = 14;
        PdfTemplate tpl = writer.getDirectContent().createTemplate(width, height);
        double max = 0;
        for (MonthRow m : r.months()) {
            max = Math.max(max, m.previousYearCumulative().doubleValue());
            if (m.cumulative() != null) {
                max = Math.max(max, m.cumulative().doubleValue());
            }
        }
        double scale = niceMax(max);
        float plotH = height - bottom - top;
        float plotW = width - left;
        grid(tpl, left, bottom, plotW, plotH, scale);
        float slot = plotW / 12f;
        line(tpl, r.months(), left, bottom, slot, plotH, scale, true);
        line(tpl, r.months(), left, bottom, slot, plotH, scale, false);
        for (MonthRow m : r.months()) {
            text(
                    tpl,
                    monthShort(m.month()),
                    left + slot * (m.month() - 1) + slot / 2f,
                    5,
                    7.5f,
                    MUTED,
                    Element.ALIGN_CENTER);
        }
        // Rótulo só na ponta da linha do ano
        MonthRow last = null;
        for (MonthRow m : r.months()) {
            if (m.cumulative() != null) {
                last = m;
            }
        }
        if (last != null) {
            float x = left + slot * (last.month() - 1) + slot / 2f;
            float y = bottom + (float) (last.cumulative().doubleValue() / scale * plotH);
            tpl.setColorFill(Color.WHITE);
            tpl.circle(x, y, 5);
            tpl.fill();
            tpl.setColorFill(SERIES);
            tpl.circle(x, y, 3.5f);
            tpl.fill();
            text(tpl, compact(last.cumulative()), x, y + 7, 7.5f, TEXT, Element.ALIGN_CENTER);
        }
        return image(tpl);
    }

    private static void line(
            PdfTemplate tpl,
            List<MonthRow> months,
            float left,
            float bottom,
            float slot,
            float plotH,
            double scale,
            boolean previous) {
        tpl.setLineWidth(2);
        tpl.setLineCap(PdfContentByte.LINE_CAP_ROUND);
        tpl.setLineJoin(PdfContentByte.LINE_JOIN_ROUND);
        tpl.setColorStroke(previous ? COMPARISON : SERIES);
        boolean started = false;
        for (MonthRow m : months) {
            BigDecimal v = previous ? m.previousYearCumulative() : m.cumulative();
            if (v == null) {
                break;
            }
            float x = left + slot * (m.month() - 1) + slot / 2f;
            float y = bottom + (float) (v.doubleValue() / scale * plotH);
            if (started) {
                tpl.lineTo(x, y);
            } else {
                tpl.moveTo(x, y);
                started = true;
            }
        }
        if (started) {
            tpl.stroke();
        }
    }

    /** Linhas de grade finas e os valores do eixo em forma curta (R$ 10 mil, R$ 1,2 mi). */
    private void grid(PdfTemplate tpl, float left, float bottom, float plotW, float plotH, double scale) {
        tpl.setLineWidth(0.5f);
        tpl.setColorStroke(GRID);
        for (int i = 0; i <= 4; i++) {
            float y = bottom + plotH * i / 4f;
            tpl.moveTo(left, y);
            tpl.lineTo(left + plotW, y);
            tpl.stroke();
            text(tpl, compact(BigDecimal.valueOf(scale * i / 4)), left - 4, y - 2.5f, 7, MUTED, Element.ALIGN_RIGHT);
        }
    }

    /** Coluna com a ponta arredondada (4 pt) e a base reta. */
    private static void column(PdfTemplate tpl, float x, float y, float w, float h, Color color) {
        if (h <= 0.3f) {
            return;
        }
        tpl.setColorFill(color);
        float radius = Math.min(3, Math.min(w / 2, h));
        tpl.roundRectangle(x, y, w, h, radius);
        tpl.fill();
        if (h > radius) {
            tpl.rectangle(x, y, w, radius);
            tpl.fill();
        }
    }

    /** Barras horizontais com nome, valor e a parte do total, e a tabela das mesmas fatias embaixo. */
    private void slices(Document doc, PdfWriter writer, String title, List<Slice> slices) {
        if (slices.isEmpty()) {
            return;
        }
        section(doc, title);
        float width = doc.right() - doc.left();
        float rowH = 16;
        float height = rowH * slices.size() + 4;
        float labelW = width * 0.38f;
        float valueW = 120;
        float barW = width - labelW - valueW - 8;
        PdfTemplate tpl = writer.getDirectContent().createTemplate(width, height);
        double max =
                slices.stream().mapToDouble(s -> s.value().doubleValue()).max().orElse(0);
        for (int i = 0; i < slices.size(); i++) {
            Slice s = slices.get(i);
            float y = height - rowH * (i + 1) + 4;
            text(tpl, fit(s.label(), labelW - 6, 8), 0, y + 2, 8, TEXT, Element.ALIGN_LEFT);
            float w = max == 0 ? 0 : (float) (s.value().doubleValue() / max * barW);
            if (w > 0.3f) {
                tpl.setColorFill(s.key() == null ? COMPARISON : SERIES);
                float radius = Math.min(3, w / 2);
                tpl.roundRectangle(labelW, y, w, 9, radius);
                tpl.fill();
                tpl.rectangle(labelW, y, Math.min(radius, w), 9);
                tpl.fill();
            }
            text(
                    tpl,
                    money(s.value()) + " · " + percent(s.share()),
                    labelW + w + 4,
                    y + 2,
                    7.5f,
                    TEXT,
                    Element.ALIGN_LEFT);
        }
        doc.add(image(tpl));
    }

    // ---- tabelas ----

    private static PdfPTable monthsTable(ReportView r) {
        PdfPTable t = new PdfPTable(new float[] {1.4f, 2f, 1f, 2f, 1.3f});
        t.setWidthPercentage(100);
        t.setKeepTogether(true);
        t.setSpacingBefore(6);
        t.setHeaderRows(1);
        header(t, "Mês", "Valor " + r.year(), "Qtde.", "Valor " + (r.year() - 1), "Sobre o mês anterior");
        for (MonthRow m : r.months()) {
            boolean best = r.summary().bestMonth() != null && r.summary().bestMonth() == m.month();
            cell(t, monthName(m.month()) + (best ? " (melhor)" : ""), best ? BODY_BOLD : BODY, Element.ALIGN_LEFT);
            cell(t, money(m.value()), best ? BODY_BOLD : BODY, Element.ALIGN_RIGHT);
            cell(t, String.valueOf(m.count()), BODY, Element.ALIGN_RIGHT);
            cell(t, money(m.previousYearValue()), BODY, Element.ALIGN_RIGHT);
            cell(
                    t,
                    m.changePercent() == null
                            ? "—"
                            : (m.changePercent().signum() > 0 ? "+" : "") + percent(m.changePercent()),
                    BODY,
                    Element.ALIGN_RIGHT);
        }
        Summary s = r.summary();
        cell(t, "Total", BODY_BOLD, Element.ALIGN_LEFT);
        cell(t, money(s.total()), BODY_BOLD, Element.ALIGN_RIGHT);
        cell(t, String.valueOf(s.count()), BODY_BOLD, Element.ALIGN_RIGHT);
        BigDecimal previousTotal =
                r.months().stream().map(MonthRow::previousYearValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        cell(t, money(previousTotal), BODY_BOLD, Element.ALIGN_RIGHT);
        cell(t, "", BODY, Element.ALIGN_RIGHT);
        return t;
    }

    /** Ano × mês, como a planilha de controle: valores na forma curta para caber em retrato. */
    private static PdfPTable yearsTable(List<YearRow> years) {
        float[] widths = new float[14];
        widths[0] = 1f;
        for (int i = 1; i <= 12; i++) {
            widths[i] = 1f;
        }
        widths[13] = 1.3f;
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        t.setSpacingBefore(4);
        t.setHeaderRows(1);
        String[] head = new String[14];
        head[0] = "Ano";
        for (int i = 1; i <= 12; i++) {
            head[i] = monthShort(i);
        }
        head[13] = "Total";
        header(t, head);
        for (YearRow y : years) {
            cell(t, String.valueOf(y.year()), BODY_BOLD, Element.ALIGN_LEFT);
            for (BigDecimal v : y.months()) {
                cell(t, v.signum() == 0 ? "—" : compactNoSymbol(v), SMALL, Element.ALIGN_RIGHT);
            }
            cell(t, compactNoSymbol(y.total()), BODY_BOLD, Element.ALIGN_RIGHT);
        }
        return t;
    }

    private static void header(PdfPTable t, String... titles) {
        for (String title : titles) {
            PdfPCell c = new PdfPCell(new Phrase(title, LABEL));
            c.setBackgroundColor(HEADER_FILL);
            c.setBorderColor(GRID);
            c.setPadding(4);
            c.setHorizontalAlignment(title.equals(titles[0]) ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            t.addCell(c);
        }
    }

    private static void cell(PdfPTable t, String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(GRID);
        c.setPadding(3.5f);
        c.setHorizontalAlignment(align);
        t.addCell(c);
    }

    private static void section(Document doc, String title) {
        Paragraph p = new Paragraph(title, SECTION);
        p.setSpacingBefore(12);
        p.setSpacingAfter(4);
        p.setKeepTogether(true);
        doc.add(p);
    }

    // ---- utilitários ----

    private void text(PdfTemplate tpl, String text, float x, float y, float size, Color color, int align) {
        tpl.beginText();
        tpl.setFontAndSize(axisFont, size);
        tpl.setColorFill(color);
        tpl.showTextAligned(align, text, x, y, 0);
        tpl.endText();
    }

    /** Corta o texto com reticências para caber na largura. */
    private String fit(String text, float width, float size) {
        if (axisFont.getWidthPoint(text, size) <= width) {
            return text;
        }
        String t = text;
        while (t.length() > 1 && axisFont.getWidthPoint(t + "…", size) > width) {
            t = t.substring(0, t.length() - 1);
        }
        return t.stripTrailing() + "…";
    }

    private static Image image(PdfTemplate tpl) {
        try {
            Image image = Image.getInstance(tpl);
            image.setAlignment(Element.ALIGN_LEFT);
            return image;
        } catch (com.lowagie.text.BadElementException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Topo do eixo com 4 divisões redondas: escolhe o passo (1, 2, 2,5 ou 5 × 10ⁿ) e o topo é 4 passos. Igual à tela.
     */
    static double niceMax(double max) {
        if (max <= 0) {
            return 4;
        }
        double raw = max / 4;
        double power = Math.pow(10, Math.floor(Math.log10(raw)));
        for (double step : new double[] {1, 2, 2.5, 5, 10}) {
            if (step * power * 4 >= max) {
                return step * power * 4;
            }
        }
        return 40 * power;
    }

    static String money(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        return NumberFormat.getCurrencyInstance(PT_BR).format(value).replace(' ', ' ');
    }

    /** R$ 950, R$ 12 mil, R$ 1,2 mi. */
    static String compact(BigDecimal value) {
        return "R$ " + compactNoSymbol(value);
    }

    static String compactNoSymbol(BigDecimal value) {
        double v = value.doubleValue();
        NumberFormat f = NumberFormat.getNumberInstance(PT_BR);
        // Meio para cima, como o toLocaleString da tela: 1,25 mi vira 1,3 mi nos dois
        f.setRoundingMode(RoundingMode.HALF_UP);
        f.setMaximumFractionDigits(1);
        if (Math.abs(v) >= 1_000_000) {
            return f.format(v / 1_000_000) + " mi";
        }
        if (Math.abs(v) >= 1_000) {
            f.setMaximumFractionDigits(0);
            return f.format(v / 1_000) + " mil";
        }
        f.setMaximumFractionDigits(0);
        return f.format(v);
    }

    private static String percent(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        NumberFormat f = NumberFormat.getNumberInstance(PT_BR);
        f.setMinimumFractionDigits(0);
        f.setMaximumFractionDigits(1);
        return f.format(value.setScale(1, RoundingMode.HALF_UP)) + "%";
    }

    private static String monthName(int month) {
        String name = Month.of(month).getDisplayName(TextStyle.FULL, PT_BR);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String monthShort(int month) {
        String name = Month.of(month).getDisplayName(TextStyle.SHORT, PT_BR).replace(".", "");
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

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
            Phrase text = new Phrase("Relatório · " + label + " · página " + writer.getPageNumber(), FOOTER);
            ColumnText.showTextAligned(
                    writer.getDirectContent(),
                    Element.ALIGN_CENTER,
                    text,
                    (document.left() + document.right()) / 2,
                    document.bottom() - (background == null ? 20 : 14),
                    0);
        }
    }
}
