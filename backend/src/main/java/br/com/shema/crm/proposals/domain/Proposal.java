package br.com.shema.crm.proposals.domain;

import br.com.shema.crm.shared.domain.BrDocuments;
import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Proposta de preços. O número é dado na criação e nunca muda: editar a proposta mantém o número, e duplicar cria
 * outra com número novo. Enquanto a pessoa digita, quase tudo pode ficar em branco; {@link #missing()} diz o que falta
 * para gerar o PDF. Cancelada, não aceita mais alteração.
 */
@Entity
@Table(name = "proposals")
public class Proposal extends AuditableEntity {

    public static final int MAX_ITEMS = 500;

    @Id
    private UUID id;

    @Column(name = "number_year", nullable = false, updatable = false)
    private int numberYear;

    @Column(name = "number_seq", nullable = false, updatable = false)
    private int numberSeq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProposalKind kind;

    @Column(name = "client_name", length = 200)
    private String clientName;

    @Column(name = "client_cnpj", length = 14)
    private String clientCnpj;

    @Column(name = "client_address", length = 300)
    private String clientAddress;

    @Column(name = "client_email", length = 254)
    private String clientEmail;

    @Column(name = "client_phones", length = 120)
    private String clientPhones;

    @Column(name = "validity_days")
    private Integer validityDays;

    @Column(name = "payment_terms", length = 500)
    private String paymentTerms;

    @Column(name = "delivery_terms", length = 500)
    private String deliveryTerms;

    @Column(name = "proposal_date", nullable = false)
    private LocalDate proposalDate;

    @Column(name = "signer_name", length = 120)
    private String signerName;

    @Column(name = "signer_role", length = 120)
    private String signerRole;

    @Column(name = "signer_cpf", length = 11)
    private String signerCpf;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "proposal_id", nullable = false)
    @OrderBy("position")
    private List<ProposalItem> items = new ArrayList<>();

    protected Proposal() {}

    /**
     * Proposta nova, já com número, a data de hoje e quem assina (o usuário que criou, com o cargo e o CPF do
     * cadastro). O resto a pessoa preenche na tela.
     */
    public static Proposal open(ProposalNumber number, LocalDate today, UUID ownerId, Signer signer) {
        Proposal p = new Proposal();
        p.id = UUID.randomUUID();
        p.numberYear = number.year();
        p.numberSeq = number.sequence();
        p.kind = ProposalKind.PRODUTO;
        p.proposalDate = Objects.requireNonNull(today, "today");
        p.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        if (signer != null) {
            p.signerName = text(signer.name(), 120, "no nome do responsável");
            p.signerRole = text(signer.role(), 120, "no cargo");
            p.signerCpf = signer.cpf();
        }
        return p;
    }

    /** Quem assina, como vem do cadastro do usuário. */
    public record Signer(String name, String role, String cpf) {}

    /** Cópia com número novo e a data de hoje, para outro cliente ou outra data. Mesmo a cancelada pode ser copiada. */
    public Proposal duplicate(ProposalNumber number, LocalDate today, UUID ownerId) {
        Proposal copy = open(number, today, ownerId, null);
        copy.kind = kind;
        copy.clientName = clientName;
        copy.clientCnpj = clientCnpj;
        copy.clientAddress = clientAddress;
        copy.clientEmail = clientEmail;
        copy.clientPhones = clientPhones;
        copy.validityDays = validityDays;
        copy.paymentTerms = paymentTerms;
        copy.deliveryTerms = deliveryTerms;
        copy.signerName = signerName;
        copy.signerRole = signerRole;
        copy.signerCpf = signerCpf;
        items.forEach(item -> copy.items.add(item.copy()));
        copy.total = total;
        return copy;
    }

    public void update(ProposalDraft d) {
        requireOpen();
        this.kind = d.kind() == null ? ProposalKind.PRODUTO : d.kind();
        this.clientName = text(d.clientName(), 200, "no nome do cliente");
        this.clientCnpj = BrDocuments.cnpj(d.clientCnpj());
        this.clientAddress = text(d.clientAddress(), 300, "no endereço");
        this.clientEmail = text(d.clientEmail(), 254, "no e-mail");
        this.clientPhones = text(d.clientPhones(), 120, "nos telefones");
        if (d.validityDays() != null && (d.validityDays() < 1 || d.validityDays() > 3650)) {
            throw new DomainRuleViolation("VALIDITY_INVALID", "A validade vai de 1 a 3650 dias.");
        }
        this.validityDays = d.validityDays();
        this.paymentTerms = text(d.paymentTerms(), 500, "na forma de pagamento");
        this.deliveryTerms = text(d.deliveryTerms(), 500, "no prazo de entrega");
        if (d.proposalDate() == null) {
            throw new DomainRuleViolation("REQUIRED", "Informe a data da proposta.");
        }
        this.proposalDate = d.proposalDate();
        this.signerName = text(d.signerName(), 120, "no nome do responsável");
        this.signerRole = text(d.signerRole(), 120, "no cargo");
        this.signerCpf = BrDocuments.cpf(d.signerCpf());
        List<ProposalItemDraft> drafts = d.items() == null ? List.of() : d.items();
        ProposalItem.requireNotTooMany(drafts.size());
        items.clear();
        for (int i = 0; i < drafts.size(); i++) {
            items.add(new ProposalItem(i + 1, drafts.get(i)));
        }
        this.total = sumItems();
    }

    public void cancel(UUID by, Instant at) {
        requireOpen();
        this.cancelledAt = at;
        this.cancelledBy = by;
    }

    public void reopen() {
        if (cancelledAt == null) {
            throw new DomainRuleViolation("PROPOSAL_NOT_CANCELLED", "A proposta não está cancelada.");
        }
        this.cancelledAt = null;
        this.cancelledBy = null;
    }

    /** O que falta para gerar o PDF, pelo nome que a tela mostra. Vazio quando a proposta está pronta. */
    public List<String> missing() {
        List<String> missing = new ArrayList<>();
        if (clientName == null) {
            missing.add("nome do cliente");
        }
        if (clientCnpj == null) {
            missing.add("CNPJ do cliente");
        }
        if (items.isEmpty()) {
            missing.add("pelo menos um item");
        } else if (items.stream().anyMatch(item -> !item.isComplete())) {
            missing.add("descrição, quantidade e valor unitário de todos os itens");
        }
        if (validityDays == null) {
            missing.add("validade da proposta");
        }
        if (paymentTerms == null) {
            missing.add("forma de pagamento");
        }
        if (deliveryTerms == null) {
            missing.add("prazo de entrega");
        }
        if (signerName == null) {
            missing.add("nome do responsável");
        }
        if (signerRole == null) {
            missing.add("cargo do responsável");
        }
        if (signerCpf == null) {
            missing.add("CPF do responsável");
        }
        return List.copyOf(missing);
    }

    public void requireReadyForDocument() {
        if (cancelledAt != null) {
            throw new DomainRuleViolation(
                    "PROPOSAL_CANCELLED", "A proposta está cancelada. Duplique para fazer outra.");
        }
        List<String> missing = missing();
        if (!missing.isEmpty()) {
            throw new DomainRuleViolation(
                    "PROPOSAL_INCOMPLETE", "Para gerar o PDF, preencha: " + String.join(", ", missing) + ".");
        }
    }

    public boolean isCancelled() {
        return cancelledAt != null;
    }

    public ProposalNumber number() {
        return new ProposalNumber(numberYear, numberSeq);
    }

    private void requireOpen() {
        if (cancelledAt != null) {
            throw new DomainRuleViolation(
                    "PROPOSAL_CANCELLED", "A proposta está cancelada. Desfaça o cancelamento para alterar.");
        }
    }

    private BigDecimal sumItems() {
        return items.stream()
                .map(ProposalItem::total)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Texto aparado, ou nulo se veio em branco; mais longo que o limite é erro de regra. */
    static String text(String value, int max, String where) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new DomainRuleViolation("TOO_LONG", "Use no máximo " + max + " caracteres " + where + ".");
        }
        return trimmed;
    }

    public UUID getId() {
        return id;
    }

    public ProposalKind getKind() {
        return kind;
    }

    public String getClientName() {
        return clientName;
    }

    public String getClientCnpj() {
        return clientCnpj;
    }

    public String getClientAddress() {
        return clientAddress;
    }

    public String getClientEmail() {
        return clientEmail;
    }

    public String getClientPhones() {
        return clientPhones;
    }

    public Integer getValidityDays() {
        return validityDays;
    }

    public String getPaymentTerms() {
        return paymentTerms;
    }

    public String getDeliveryTerms() {
        return deliveryTerms;
    }

    public LocalDate getProposalDate() {
        return proposalDate;
    }

    public String getSignerName() {
        return signerName;
    }

    public String getSignerRole() {
        return signerRole;
    }

    public String getSignerCpf() {
        return signerCpf;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public List<ProposalItem> getItems() {
        return List.copyOf(items);
    }
}
