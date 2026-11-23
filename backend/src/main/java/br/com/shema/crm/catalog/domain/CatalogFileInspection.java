package br.com.shema.crm.catalog.domain;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * Confere a foto ou a ficha técnica antes de guardar: até 10 MB, JPG, PNG, WEBP ou PDF. O tipo vem do conteúdo
 * (primeiros bytes) e precisa bater com a extensão; o tipo informado pelo navegador é ignorado.
 */
public final class CatalogFileInspection {

    public static final int MAX_BYTES = 10 * 1024 * 1024;

    /** O que foi aceito: nome limpo e o tipo servido no download. */
    public record Accepted(String fileName, String contentType) {}

    private static final Map<String, String> TYPES = Map.of(
            "pdf", "application/pdf",
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "webp", "image/webp");

    private CatalogFileInspection() {}

    public static Accepted inspect(ProductFileKind kind, String originalName, byte[] content) {
        if (content == null || content.length == 0) {
            throw new DomainRuleViolation("FILE_EMPTY", "O arquivo está vazio.");
        }
        if (content.length > MAX_BYTES) {
            throw new DomainRuleViolation("FILE_TOO_LARGE", "O arquivo passa de 10 MB.");
        }
        String name = cleanName(originalName);
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        String type = TYPES.get(extension);
        if (type == null || (kind == ProductFileKind.FOTO && !type.startsWith("image/"))) {
            throw new DomainRuleViolation(
                    "FILE_TYPE_NOT_ALLOWED",
                    kind == ProductFileKind.FOTO
                            ? "A foto precisa ser JPG, PNG ou WEBP."
                            : "A ficha técnica precisa ser PDF, JPG, PNG ou WEBP.");
        }
        if (!type.equals(typeOf(content))) {
            throw new DomainRuleViolation(
                    "FILE_TYPE_MISMATCH",
                    "O conteúdo do arquivo não é de um ." + extension + ". Confira se o arquivo não está corrompido.");
        }
        return new Accepted(name, type);
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

    private static String typeOf(byte[] c) {
        if (starts(c, 0, '%', 'P', 'D', 'F', '-')) {
            return "application/pdf";
        }
        if (starts(c, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (starts(c, 0, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (starts(c, 0, 'R', 'I', 'F', 'F') && starts(c, 8, 'W', 'E', 'B', 'P')) {
            return "image/webp";
        }
        return null;
    }

    private static boolean starts(byte[] content, int offset, int... prefix) {
        if (content.length < offset + prefix.length) {
            return false;
        }
        byte[] expected = new byte[prefix.length];
        for (int i = 0; i < prefix.length; i++) {
            expected[i] = (byte) prefix[i];
        }
        return Arrays.equals(content, offset, offset + prefix.length, expected, 0, prefix.length);
    }
}
