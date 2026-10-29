package br.com.shema.crm.pricerecords.domain;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * Confere um arquivo enviado antes de guardar. O tipo vem do conteúdo (assinatura dos primeiros bytes), conferido
 * com a extensão: um ".pdf" que na verdade é outra coisa é recusado. O tipo informado pelo navegador é ignorado.
 */
public final class FileInspection {

    public static final int MAX_BYTES = 25 * 1024 * 1024;

    /** O que foi aceito: nome limpo e o tipo que será servido no download. */
    public record Accepted(String fileName, String contentType) {}

    private enum Signature {
        PDF,
        PNG,
        JPEG,
        ZIP,
        OLE
    }

    /** Extensão → assinatura esperada e tipo servido. Office novo e ODF são ZIP; Office antigo é OLE. */
    private static final Map<String, Map.Entry<Signature, String>> ALLOWED = Map.ofEntries(
            Map.entry("pdf", Map.entry(Signature.PDF, "application/pdf")),
            Map.entry("png", Map.entry(Signature.PNG, "image/png")),
            Map.entry("jpg", Map.entry(Signature.JPEG, "image/jpeg")),
            Map.entry("jpeg", Map.entry(Signature.JPEG, "image/jpeg")),
            Map.entry(
                    "docx",
                    Map.entry(
                            Signature.ZIP, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
            Map.entry(
                    "xlsx",
                    Map.entry(Signature.ZIP, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
            Map.entry("odt", Map.entry(Signature.ZIP, "application/vnd.oasis.opendocument.text")),
            Map.entry("ods", Map.entry(Signature.ZIP, "application/vnd.oasis.opendocument.spreadsheet")),
            Map.entry("doc", Map.entry(Signature.OLE, "application/msword")),
            Map.entry("xls", Map.entry(Signature.OLE, "application/vnd.ms-excel")));

    private FileInspection() {}

    public static Accepted inspect(String originalName, byte[] content) {
        if (content == null || content.length == 0) {
            throw new DomainRuleViolation("FILE_EMPTY", "O arquivo está vazio.");
        }
        if (content.length > MAX_BYTES) {
            throw new DomainRuleViolation("FILE_TOO_LARGE", "O arquivo passa de 25 MB.");
        }
        String name = cleanName(originalName);
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        Map.Entry<Signature, String> expected = ALLOWED.get(extension);
        if (expected == null) {
            throw new DomainRuleViolation(
                    "FILE_TYPE_NOT_ALLOWED",
                    "Tipo de arquivo não aceito. Envie PDF, imagem (PNG ou JPG), Word, Excel ou ODF.");
        }
        if (signatureOf(content) != expected.getKey()) {
            throw new DomainRuleViolation(
                    "FILE_TYPE_MISMATCH",
                    "O conteúdo do arquivo não é de um ." + extension + ". Confira se o arquivo não está corrompido.");
        }
        return new Accepted(name, expected.getValue());
    }

    /** Só o nome (sem pasta), sem caracteres de controle e com no máximo 255 caracteres, preservando a extensão. */
    static String cleanName(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            name = "arquivo";
        }
        if (name.length() > 255) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 && name.length() - dot <= 10 ? name.substring(dot) : "";
            name = name.substring(0, 255 - ext.length()) + ext;
        }
        return name;
    }

    private static Signature signatureOf(byte[] c) {
        if (starts(c, '%', 'P', 'D', 'F', '-')) {
            return Signature.PDF;
        }
        if (starts(c, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Signature.PNG;
        }
        if (starts(c, 0xFF, 0xD8, 0xFF)) {
            return Signature.JPEG;
        }
        if (starts(c, 'P', 'K', 0x03, 0x04)) {
            return Signature.ZIP;
        }
        if (starts(c, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) {
            return Signature.OLE;
        }
        return null;
    }

    private static boolean starts(byte[] content, int... prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        byte[] expected = new byte[prefix.length];
        for (int i = 0; i < prefix.length; i++) {
            expected[i] = (byte) prefix[i];
        }
        return Arrays.equals(content, 0, prefix.length, expected, 0, prefix.length);
    }
}
