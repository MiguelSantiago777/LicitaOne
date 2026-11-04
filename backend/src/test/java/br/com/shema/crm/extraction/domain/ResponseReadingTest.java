package br.com.shema.crm.extraction.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResponseReadingTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

    private static RawResponse raw(String outcome, String expected, boolean vague, String title, String due) {
        return new RawResponse(outcome, expected, vague, "Pedem em março.", title, due, List.of());
    }

    @Test
    void keepsTheExpectedDateAndTheSuggestedTask() {
        ResponseReading r = ResponseReading.from(
                raw("pedido_previsto", "2027-03-01", false, "Cobrar o pedido de março", "2027-02-25"), TODAY, TODAY);

        assertThat(r.outcome()).isEqualTo("PEDIDO_PREVISTO");
        assertThat(r.expectedDate()).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(r.taskDueDate()).isEqualTo(LocalDate.of(2027, 2, 25));
        assertThat(r.summary()).isEqualTo("Pedem em março.");
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    void aTaskWithoutDateFallsOnTheExpectedDateAndNeverBeforeToday() {
        assertThat(ResponseReading.from(raw("PEDIDO_PREVISTO", "2027-03-01", false, "Cobrar", ""), TODAY, TODAY)
                        .taskDueDate())
                .isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(ResponseReading.from(raw("PEDE_DOCUMENTO", "", false, "Mandar certidão", "2026-10-01"), TODAY, TODAY)
                        .taskDueDate())
                .isEqualTo(TODAY);
        assertThat(ResponseReading.from(raw("SEM_PREVISAO", "", false, "Ligar de novo", ""), TODAY, TODAY)
                        .taskDueDate())
                .isEqualTo(TODAY.plusDays(7));
    }

    @Test
    void warnsAboutVagueDatesOrdersWithoutDateAndDatesBeforeTheContact() {
        assertThat(ResponseReading.from(raw("PEDIDO_PREVISTO", "2027-02-15", true, "", ""), TODAY, TODAY)
                        .warnings())
                .anySatisfy(w -> assertThat(w).contains("estimativa"));
        assertThat(ResponseReading.from(raw("PEDIDO_PREVISTO", "", false, "", ""), TODAY, TODAY)
                        .warnings())
                .anySatisfy(w -> assertThat(w).contains("sem data"));
        ResponseReading before =
                ResponseReading.from(raw("PEDIDO_PREVISTO", "2026-01-10", false, "", ""), TODAY, TODAY);
        assertThat(before.expectedDate()).isNull();
        assertThat(before.warnings()).anySatisfy(w -> assertThat(w).contains("anterior ao contato"));
    }

    @Test
    void anUnknownOutcomeBecomesOtherAndNoTitleMeansNoTask() {
        ResponseReading r = ResponseReading.from(raw("TALVEZ", "", false, "", "2026-12-01"), TODAY, TODAY);

        assertThat(r.outcome()).isEqualTo("OUTRO");
        assertThat(r.taskTitle()).isNull();
        assertThat(r.taskDueDate()).isNull();
    }

    @Test
    void followUpKeepsTheSubjectOnlyForEmailAndWarnsAboutLongWhatsApp() {
        FollowUpDraft email = FollowUpDraft.from(new RawFollowUp("", "Prezados(as), ...", List.of()), false);
        assertThat(email.subject()).isEqualTo("Previsão de pedidos");

        FollowUpDraft whatsapp = FollowUpDraft.from(new RawFollowUp("Assunto", "x".repeat(800), List.of()), true);
        assertThat(whatsapp.subject()).isNull();
        assertThat(whatsapp.warnings()).anySatisfy(w -> assertThat(w).contains("longo para WhatsApp"));

        assertThatThrownBy(() -> FollowUpDraft.from(new RawFollowUp("A", " ", List.of()), false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
