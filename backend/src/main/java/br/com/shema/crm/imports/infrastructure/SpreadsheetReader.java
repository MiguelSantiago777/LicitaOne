package br.com.shema.crm.imports.infrastructure;

import br.com.shema.crm.imports.domain.SheetData;
import br.com.shema.crm.imports.domain.SheetRow;
import br.com.shema.crm.shared.domain.DomainRuleViolation;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Component;

/**
 * Lê .xlsx e .csv como texto, célula a célula. Números saem sem formatação ("4.1", "287201.54"), fórmulas pelo valor
 * calculado que a planilha guardou, e células mescladas repetem o valor da primeira (o lote mesclado em várias linhas
 * vale para todas).
 */
@Component
public class SpreadsheetReader {

    static final int MAX_ROWS = 5000;
    private static final char BOM = (char) 0xFEFF;
    static final int MAX_COLUMNS = 60;

    public List<SheetData> read(String fileName, byte[] content) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".csv")) {
            return List.of(readCsv(content));
        }
        if (lower.endsWith(".xlsx") && isZip(content)) {
            return readXlsx(content);
        }
        throw new DomainRuleViolation("FILE_TYPE", "Envie uma planilha .xlsx ou .csv.");
    }

    private List<SheetData> readXlsx(byte[] content) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            List<SheetData> sheets = new ArrayList<>();
            for (Sheet sheet : workbook) {
                sheets.add(new SheetData(sheet.getSheetName().trim(), rows(sheet)));
            }
            return sheets;
        } catch (IOException | RuntimeException e) {
            if (e instanceof DomainRuleViolation rule) {
                throw rule;
            }
            throw new DomainRuleViolation("FILE_UNREADABLE", "Não foi possível ler a planilha. Ela abre no Excel?");
        }
    }

    private List<SheetRow> rows(Sheet sheet) {
        Map<Long, String> merged = mergedValues(sheet);
        List<SheetRow> rows = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getRowNum() >= MAX_ROWS) {
                throw new DomainRuleViolation(
                        "FILE_TOO_LARGE",
                        "A aba \"" + sheet.getSheetName().trim() + "\" passa de " + MAX_ROWS + " linhas.");
            }
            int last = Math.min(Math.max(row.getLastCellNum(), 0), MAX_COLUMNS);
            List<String> cells = new ArrayList<>(last);
            boolean any = false;
            for (int c = 0; c < last; c++) {
                String value = merged.getOrDefault(position(row.getRowNum(), c), text(row.getCell(c)));
                cells.add(value);
                any |= value != null;
            }
            if (any) {
                rows.add(new SheetRow(row.getRowNum() + 1, cells));
            }
        }
        return rows;
    }

    private Map<Long, String> mergedValues(Sheet sheet) {
        Map<Long, String> values = new HashMap<>();
        for (CellRangeAddress range : sheet.getMergedRegions()) {
            Row first = sheet.getRow(range.getFirstRow());
            String value = first == null ? null : text(first.getCell(range.getFirstColumn()));
            if (value == null) {
                continue;
            }
            for (int r = range.getFirstRow(); r <= Math.min(range.getLastRow(), MAX_ROWS); r++) {
                for (int c = range.getFirstColumn(); c <= Math.min(range.getLastColumn(), MAX_COLUMNS); c++) {
                    values.put(position(r, c), value);
                }
            }
        }
        return values;
    }

    private static long position(int row, int column) {
        return ((long) row << 16) | column;
    }

    private static String text(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        String value = switch (type) {
            case NUMERIC ->
                DateUtil.isCellDateFormatted(cell)
                        ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                        : BigDecimal.valueOf(cell.getNumericCellValue())
                                .stripTrailingZeros()
                                .toPlainString();
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
        if (value == null) {
            return null;
        }
        value = value.strip();
        return value.isEmpty() ? null : value;
    }

    private SheetData readCsv(byte[] content) {
        String text = decode(content);
        char delimiter =
                text.lines().findFirst().map(SpreadsheetReader::delimiter).orElse(';');
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(delimiter).get();
        List<SheetRow> rows = new ArrayList<>();
        try {
            for (CSVRecord record : format.parse(new StringReader(text))) {
                if (record.getRecordNumber() > MAX_ROWS) {
                    throw new DomainRuleViolation("FILE_TOO_LARGE", "O arquivo passa de " + MAX_ROWS + " linhas.");
                }
                List<String> cells = new ArrayList<>();
                boolean any = false;
                for (int c = 0; c < Math.min(record.size(), MAX_COLUMNS); c++) {
                    String value = record.get(c).strip();
                    cells.add(value.isEmpty() ? null : value);
                    any |= !value.isEmpty();
                }
                if (any) {
                    rows.add(new SheetRow((int) record.getRecordNumber(), cells));
                }
            }
        } catch (IOException | IllegalStateException | java.io.UncheckedIOException e) {
            throw new DomainRuleViolation(
                    "FILE_UNREADABLE", "Não foi possível ler o CSV: confira as aspas e o separador.");
        }
        return new SheetData("CSV", rows);
    }

    /** O separador mais frequente na primeira linha: ";" (Excel em português) ou ",". */
    private static char delimiter(String firstLine) {
        long semicolons = firstLine.chars().filter(ch -> ch == ';').count();
        long commas = firstLine.chars().filter(ch -> ch == ',').count();
        return commas > semicolons ? ',' : ';';
    }

    /** UTF-8 (com ou sem BOM); se não for UTF-8 válido, Windows-1252, que é o que o Excel salva em português. */
    private static String decode(byte[] content) {
        try {
            String text = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
            return text.startsWith(String.valueOf(BOM)) ? text.substring(1) : text;
        } catch (CharacterCodingException e) {
            return new String(content, Charset.forName("windows-1252"));
        }
    }

    private static boolean isZip(byte[] content) {
        return content.length > 4 && content[0] == 'P' && content[1] == 'K';
    }
}
