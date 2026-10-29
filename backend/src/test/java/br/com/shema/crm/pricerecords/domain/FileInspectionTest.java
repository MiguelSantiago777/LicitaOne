package br.com.shema.crm.pricerecords.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FileInspectionTest {

    private static final byte[] PDF = "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP = {'P', 'K', 3, 4, 20, 0};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0};

    private static void assertRule(Runnable action, String rule) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DomainRuleViolation.class)
                .extracting(e -> ((DomainRuleViolation) e).rule())
                .isEqualTo(rule);
    }

    @Test
    void typeComesFromTheContentAndMatchesTheExtension() {
        assertThat(FileInspection.inspect("Ata 017-2026.PDF", PDF))
                .isEqualTo(new FileInspection.Accepted("Ata 017-2026.PDF", "application/pdf"));
        assertThat(FileInspection.inspect("contrato.docx", ZIP).contentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(FileInspection.inspect("foto.png", PNG).contentType()).isEqualTo("image/png");

        // um executável renomeado para .pdf, ou um PDF renomeado para .png
        assertRule(() -> FileInspection.inspect("ata.pdf", new byte[] {'M', 'Z', 0, 0}), "FILE_TYPE_MISMATCH");
        assertRule(() -> FileInspection.inspect("ata.png", PDF), "FILE_TYPE_MISMATCH");
        assertRule(() -> FileInspection.inspect("script.html", PDF), "FILE_TYPE_NOT_ALLOWED");
        assertRule(() -> FileInspection.inspect("sem-extensao", PDF), "FILE_TYPE_NOT_ALLOWED");
    }

    @Test
    void emptyAndTooLargeAreRefused() {
        assertRule(() -> FileInspection.inspect("ata.pdf", new byte[0]), "FILE_EMPTY");
        byte[] big = new byte[FileInspection.MAX_BYTES + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        assertRule(() -> FileInspection.inspect("ata.pdf", big), "FILE_TOO_LARGE");
    }

    @Test
    void nameLosesFoldersAndControlCharacters() {
        assertThat(FileInspection.cleanName("C:\\Users\\edwin\\Downloads\\ata.pdf"))
                .isEqualTo("ata.pdf");
        assertThat(FileInspection.cleanName("../../etc/ata.pdf")).isEqualTo("ata.pdf");
        assertThat(FileInspection.cleanName("ata\u0000\"x\".pdf")).isEqualTo("atax.pdf");
        assertThat(FileInspection.cleanName("..")).isEqualTo("arquivo");
        String longName = "a".repeat(300) + ".pdf";
        assertThat(FileInspection.cleanName(longName)).hasSize(255).endsWith(".pdf");
    }
}
