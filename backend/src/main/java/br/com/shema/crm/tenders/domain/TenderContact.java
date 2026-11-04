package br.com.shema.crm.tenders.domain;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Um contato com o órgão (Fase 9): canal, data, o que foi conversado e, se a pessoa classificou, a situação do pedido.
 * Fica no histórico da licitação e serve de contexto para o follow-up. Registrar um contato não muda a licitação.
 */
@Entity
@Table(name = "tender_contacts")
public class TenderContact {

    public enum Channel {
        EMAIL("E-mail"),
        WHATSAPP("WhatsApp"),
        TELEFONE("Telefone"),
        REUNIAO("Reunião"),
        OUTRO("Outro");

        private final String label;

        Channel(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** O que a resposta do órgão diz sobre o próximo pedido. */
    public enum Outcome {
        PEDIDO_PREVISTO("Pedido previsto"),
        SEM_PREVISAO("Sem previsão"),
        PEDIDO_A_CAMINHO("Pedido a caminho"),
        NAO_VAI_PEDIR("Não vai pedir"),
        PEDE_DOCUMENTO("Pede documento ou informação"),
        OUTRO("Outro");

        private final String label;

        Outcome(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static final int MAX_SUMMARY = 1000;

    @Id
    private UUID id;

    @Column(name = "tender_id", nullable = false, updatable = false)
    private UUID tenderId;

    @Column(name = "contract_id", updatable = false)
    private UUID contractId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, updatable = false)
    private Outcome outcome;

    @Column(name = "contacted_at", nullable = false, updatable = false)
    private LocalDate contactedAt;

    @Column(nullable = false, length = MAX_SUMMARY, updatable = false)
    private String summary;

    @Column(name = "suggested_by_ai", nullable = false, updatable = false)
    private boolean suggestedByAi;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "user_name", length = 120, updatable = false)
    private String userName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TenderContact() {}

    /**
     * @param contractId contrato de que se falou, se for o caso (precisa ser da licitação: o serviço confere)
     * @param suggestedByAi a situação e o resumo vieram da IA e foram conferidos por quem salvou
     */
    public static TenderContact register(
            UUID tenderId,
            UUID contractId,
            Channel channel,
            Outcome outcome,
            LocalDate contactedAt,
            String summary,
            boolean suggestedByAi,
            UUID userId,
            String userName,
            LocalDate today,
            Instant now) {
        if (channel == null) {
            throw new DomainRuleViolation("CONTACT_CHANNEL_REQUIRED", "Informe o canal do contato.");
        }
        if (contactedAt == null) {
            throw new DomainRuleViolation("CONTACT_DATE_REQUIRED", "Informe a data do contato.");
        }
        if (contactedAt.isAfter(today)) {
            throw new DomainRuleViolation("CONTACT_DATE_IN_FUTURE", "A data do contato não pode ser no futuro.");
        }
        String text = summary == null ? "" : summary.strip();
        if (text.isEmpty()) {
            throw new DomainRuleViolation("CONTACT_SUMMARY_REQUIRED", "Escreva o que foi conversado.");
        }
        if (text.length() > MAX_SUMMARY) {
            throw new DomainRuleViolation(
                    "CONTACT_SUMMARY_TOO_LONG", "O resumo passa de 1.000 caracteres. Deixe só o essencial.");
        }
        TenderContact c = new TenderContact();
        c.id = UUID.randomUUID();
        c.tenderId = Objects.requireNonNull(tenderId, "tenderId");
        c.contractId = contractId;
        c.channel = channel;
        c.outcome = outcome;
        c.contactedAt = contactedAt;
        c.summary = text;
        c.suggestedByAi = suggestedByAi;
        c.userId = userId;
        c.userName = userName;
        c.createdAt = now;
        return c;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenderId() {
        return tenderId;
    }

    public UUID getContractId() {
        return contractId;
    }

    public Channel getChannel() {
        return channel;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public LocalDate getContactedAt() {
        return contactedAt;
    }

    public String getSummary() {
        return summary;
    }

    public boolean isSuggestedByAi() {
        return suggestedByAi;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
