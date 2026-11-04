package br.com.shema.crm.extraction.infrastructure;

import br.com.shema.crm.extraction.application.LetterDocuments;
import br.com.shema.crm.extraction.domain.CompanyData;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.stereotype.Component;

/**
 * Carta em .docx com o Apache POI: A4, margens de 2,5 cm, Calibri 11. Sem modelo de papel timbrado (decisão de
 * 04/11/2026): o cabeçalho é o texto com os dados da empresa.
 */
@Component
class PoiLetterDocuments implements LetterDocuments {

    private static final String FONT = "Calibri";

    /** 2,5 cm em twips (1 cm = 567). */
    private static final BigInteger MARGIN = BigInteger.valueOf(1418);

    @Override
    public byte[] docx(String text, CompanyData company, boolean letterhead) {
        try (XWPFDocument doc = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            page(doc);
            if (letterhead) {
                header(doc, company);
            }
            body(doc, text == null ? "" : text);
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void page(XWPFDocument doc) {
        CTSectPr section = doc.getDocument().getBody().addNewSectPr();
        section.addNewPgSz().setW(BigInteger.valueOf(11906));
        section.getPgSz().setH(BigInteger.valueOf(16838));
        CTPageMar margins = section.addNewPgMar();
        margins.setTop(MARGIN);
        margins.setBottom(MARGIN);
        margins.setLeft(MARGIN);
        margins.setRight(MARGIN);
    }

    private static void header(XWPFDocument doc, CompanyData company) {
        XWPFParagraph name = doc.createParagraph();
        name.setAlignment(ParagraphAlignment.CENTER);
        name.setSpacingAfter(0);
        run(name, company.legalNameOr(), true, 14);
        XWPFParagraph details = doc.createParagraph();
        details.setAlignment(ParagraphAlignment.CENTER);
        details.setSpacingAfter(0);
        run(details, "CNPJ " + company.cnpjOr() + " · " + company.addressOr(), false, 9);
        String contact = company.contactLine();
        if (!contact.isEmpty()) {
            XWPFParagraph line = doc.createParagraph();
            line.setAlignment(ParagraphAlignment.CENTER);
            line.setSpacingAfter(0);
            run(line, contact, false, 9);
        }
        XWPFParagraph rule = doc.createParagraph();
        rule.setBorderBottom(Borders.SINGLE);
        rule.setSpacingAfter(240);
    }

    /** Linha em branco separa parágrafos; linhas seguidas viram quebras de linha no mesmo parágrafo. */
    private static void body(XWPFDocument doc, String text) {
        String[] blocks = text.replace("\r\n", "\n").split("\n\\s*\n");
        for (String block : blocks) {
            String[] lines = block.split("\n");
            XWPFParagraph paragraph = null;
            boolean afterBullet = false;
            for (String line : lines) {
                boolean bullet = line.startsWith("- ");
                if (bullet || afterBullet || paragraph == null) {
                    paragraph = doc.createParagraph();
                    paragraph.setSpacingAfter(bullet ? 60 : 160);
                    if (bullet) {
                        paragraph.setIndentationLeft(360);
                        paragraph.setIndentationHanging(220);
                    }
                    run(paragraph, bullet ? "• " + line.substring(2) : line, false, 11);
                } else {
                    XWPFRun br = paragraph.createRun();
                    br.addBreak();
                    run(paragraph, line, false, 11);
                }
                afterBullet = bullet;
            }
        }
    }

    private static void run(XWPFParagraph paragraph, String text, boolean bold, int size) {
        XWPFRun run = paragraph.createRun();
        run.setFontFamily(FONT);
        run.setFontSize(size);
        run.setBold(bold);
        run.setText(text);
    }
}
