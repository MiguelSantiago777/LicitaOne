package br.com.shema.crm.imports;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Planilha de teste no formato real do cliente (aba de órgão): título mesclado na linha 3, cabeçalho na linha 5,
 * fórmulas, colunas de pedido ao lado das de item, totais soltos e uma aba com lote mesclado.
 */
public final class SefazSheet {

    private SefazSheet() {}

    public static byte[] xlsx(boolean withInvalidRow) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet geral = wb.createSheet("GERAL ");
            geral.createRow(4).createCell(1).setCellValue("ORGÃOS ");

            Sheet s = wb.createSheet(" SEFAZ - CONTRATO ");
            s.createRow(2).createCell(1).setCellValue("CONTROLE DE LICITAÇÕES VENCIDAS -SEFAZ ");
            s.addMergedRegion(new CellRangeAddress(2, 2, 1, 10));
            String[] header = {
                "ITENS ",
                "DESCRIÇÃO ",
                "QUANTIDADE",
                "V.U. ",
                "V.T.  ",
                "FALTA PEDIR",
                "PEDIDO  QTD ",
                "PEDIDO V.U. ",
                "PEDIDO V.T. ",
                "TOTAL "
            };
            Row h = s.createRow(4);
            for (int i = 0; i < header.length; i++) {
                h.createCell(i + 1).setCellValue(header[i]);
            }
            item(s, 5, 1, "CARTÕES DE VISITA 0.09X0.05 CM ", 4800, 0.2, 1600);
            item(s, 6, 2, "PRISMA DE MESA 10.5X10.5 CM ", 240, 19, 0);
            item(s, 7, 3, "ADESIVO VEÍCULO 29,7X42,0 CM ", 24, 18, 0);
            item(s, 8, 4, "ADESIVO VEÍCULO 29,7X42,0 CM ", 240, 18, 24);
            item(s, 9, 5, "CANETA ESCOLAR PERSONALIZADA AZUL ", 2400, 4.0999999999999996, 1200);
            if (withInvalidRow) {
                Row bad = s.createRow(10);
                bad.createCell(1).setCellValue(6);
                bad.createCell(2).setCellValue("CARTAZ A2 594X420MM");
                bad.createCell(3).setCellValue("setenta e dois");
                bad.createCell(4).setCellValue(8);
            }
            // totais soltos, fora das colunas de item
            s.createRow(12).createCell(5).setCellFormula("SUM(F6:F11)");
            Row totals = s.createRow(13);
            totals.createCell(8).setCellValue("TOTAL DE PEDIDOS");
            totals.createCell(9).setCellFormula("SUM(J6:J11)");

            Sheet segov = wb.createSheet("SEGOV  UNIFORMES ");
            Row sh = segov.createRow(4);
            String[] segovHeader = {"LOTE", "ITENS", "DESCRIÇÃO ", "QUANTIDADE", "V.U. ", "V.T.  ", "PEDIDO  QTD "};
            for (int i = 0; i < segovHeader.length; i++) {
                sh.createCell(i + 1).setCellValue(segovHeader[i]);
            }
            Row l1 = segov.createRow(5);
            l1.createCell(1).setCellValue(1);
            l1.createCell(3).setCellValue("COLETE SEGURANÇA PRESENTE");
            l1.createCell(4).setCellValue(25610);
            l1.createCell(5).setCellValue(90.33);
            Row l2 = segov.createRow(6);
            l2.createCell(3).setCellValue("COLETE SEGOV");
            l2.createCell(4).setCellValue(520);
            l2.createCell(5).setCellValue(90.33);
            segov.addMergedRegion(new CellRangeAddress(5, 6, 1, 1));

            wb.getCreationHelper().createFormulaEvaluator().evaluateAll();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void item(
            Sheet s, int row, int number, String description, double qty, double price, double ordered) {
        Row r = s.createRow(row);
        int excelRow = row + 1;
        r.createCell(1).setCellValue(number);
        r.createCell(2).setCellValue(description);
        r.createCell(3).setCellValue(qty);
        r.createCell(4).setCellValue(price);
        r.createCell(5).setCellFormula("E" + excelRow + "*D" + excelRow);
        r.createCell(6).setCellFormula("SUM(D" + excelRow + "-H" + excelRow + ")");
        r.createCell(7).setCellValue(ordered);
        r.createCell(8).setCellValue(price);
        r.createCell(9).setCellFormula("I" + excelRow + "*H" + excelRow);
    }
}
