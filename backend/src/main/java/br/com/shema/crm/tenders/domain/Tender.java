package br.com.shema.crm.tenders.domain;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.domain.Money;
import br.com.shema.crm.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Licitação: agregado com a máquina de estados do ciclo de vida. Toda regra de transição mora aqui. */
@Entity
@Table(name = "tenders")
public class Tender extends AuditableEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id")
    private Agency agency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Modality modality;

    /** Guardado como texto; validado e exposto pelo value object {@link Uasg}. */
    @Column(length = 6)
    private String uasg;

    /** Guardado como texto; validado e exposto pelo value object {@link ProcessNumber}. */
    @Column(name = "process_number", length = 10)
    private String processNumber;

    @Column(length = 80)
    private String portal;

    @Column(name = "session_at")
    private OffsetDateTime sessionAt;

    @Column(nullable = false, length = 2000)
    private String object;

    @Column(name = "material_type", nullable = false, length = 120)
    private String materialType;

    @Column(name = "items_quantity")
    private Integer itemsQuantity;

    /** Guardado como numeric(15,2); validado e exposto pelo value object {@link Money}. */
    @Column(name = "total_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TenderStatus status;

    @Column(name = "won_at")
    private LocalDate wonAt;

    @Column(name = "signed_at")
    private LocalDate signedAt;

    @Column(name = "orders_started_at")
    private LocalDate ordersStartedAt;

    @Column(name = "closed_at")
    private LocalDate closedAt;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(length = 4000)
    private String notes;

    protected Tender() {}

    /** Cadastra em disputa (antes do resultado) ou já vencida (com a data da vitória). */
    public static Tender register(TenderDraft draft, TenderStatus initialStatus, LocalDate wonAt, LocalDate today) {
        if (initialStatus == null || !initialStatus.isInitial()) {
            throw new DomainRuleViolation(
                    "INITIAL_STATUS_INVALID", "Uma licitação nova começa em disputa ou já vencida.");
        }
        Tender tender = new Tender();
        tender.id = UUID.randomUUID();
        tender.status = TenderStatus.EM_DISPUTA;
        tender.apply(draft);
        if (initialStatus == TenderStatus.VENCIDA) {
            tender.transitionTo(TenderStatus.VENCIDA, wonAt, today);
        }
        return tender;
    }

    public void update(TenderDraft draft) {
        apply(draft);
    }

    /** Muda de estado validando o caminho permitido e a data que o novo estado exige. */
    public void transitionTo(TenderStatus target, LocalDate date, LocalDate today) {
        Objects.requireNonNull(target, "target");
        if (!status.canMoveTo(target)) {
            throw new DomainRuleViolation(
                    "TRANSITION_NOT_ALLOWED",
                    "Não é possível passar de \"" + status.label() + "\" para \"" + target.label() + "\".");
        }
        TenderStatus.DateKind kind = target.requiredDate();
        if (kind != null) {
            if (date == null) {
                throw new DomainRuleViolation("DATE_REQUIRED", "Informe a " + kind.label() + ".");
            }
            if (date.isAfter(today)) {
                throw new DomainRuleViolation("DATE_IN_FUTURE", "A " + kind.label() + " não pode ser no futuro.");
            }
            switch (kind) {
                case WON -> wonAt = date;
                case SIGNED -> {
                    requireNotBefore(date, wonAt, "A data de assinatura não pode ser antes da data da vitória.");
                    signedAt = date;
                }
                case ORDERS_STARTED -> {
                    requireNotBefore(date, signedAt, "O primeiro pedido não pode ser antes da assinatura.");
                    ordersStartedAt = date;
                }
                case CLOSED -> {
                    requireNotBefore(date, signedAt, "O encerramento não pode ser antes da assinatura.");
                    closedAt = date;
                }
            }
        }
        status = target;
    }

    /**
     * Um contrato foi assinado. O primeiro move a licitação para "Contrato assinado"; os seguintes (outros
     * contratos ou empenhos) não mudam nada. Devolve se o estado mudou.
     */
    public boolean onContractSigned(LocalDate date, LocalDate today) {
        requireAcceptsContracts();
        if (status == TenderStatus.CONTRATO_ASSINADO || status == TenderStatus.EM_ANDAMENTO) {
            return false;
        }
        transitionTo(TenderStatus.CONTRATO_ASSINADO, date, today);
        return true;
    }

    /** Um pedido foi registrado. O primeiro move a licitação para "Em andamento". Devolve se o estado mudou. */
    public boolean onOrderRegistered(LocalDate date, LocalDate today) {
        requireAcceptsContracts();
        if (status != TenderStatus.CONTRATO_ASSINADO) {
            return false;
        }
        transitionTo(TenderStatus.EM_ANDAMENTO, date, today);
        return true;
    }

    /** O último contrato aberto foi encerrado: a licitação encerra junto. Devolve se o estado mudou. */
    public boolean onAllContractsClosed(LocalDate date, LocalDate today) {
        if (status != TenderStatus.CONTRATO_ASSINADO && status != TenderStatus.EM_ANDAMENTO) {
            return false;
        }
        transitionTo(TenderStatus.ENCERRADO, date, today);
        return true;
    }

    private void requireAcceptsContracts() {
        if (!status.acceptsContracts()) {
            throw new DomainRuleViolation(
                    "TENDER_NOT_CONTRACTABLE",
                    "Só uma licitação vencida e ainda não encerrada pode receber contratos e pedidos.");
        }
    }

    /** "UASG 160221 / PE 039/2026", como o cliente escreve. */
    public String reference() {
        String process = processNumber == null ? null : modality.prefix() + " " + processNumber;
        if (uasg != null && process != null) {
            return "UASG " + uasg + " / " + process;
        }
        return uasg != null ? "UASG " + uasg : process;
    }

    /** Fotografia dos campos editáveis, para o histórico mostrar o que mudou. */
    public Map<String, String> snapshot() {
        Map<String, String> s = new LinkedHashMap<>();
        s.put("Órgão", agency.getName());
        s.put("Modalidade", modality.label());
        s.put("UASG", uasg);
        s.put("Número do processo", processNumber);
        s.put("Portal", portal);
        s.put("Sessão", sessionAt == null ? null : sessionAt.toString());
        s.put("Objeto", object);
        s.put("Tipo de material", materialType);
        s.put("Quantidade de itens", itemsQuantity == null ? null : itemsQuantity.toString());
        s.put("Valor", totalValue.toPlainString());
        s.put("Responsável", ownerId == null ? null : ownerId.toString());
        s.put("Observações", notes);
        return s;
    }

    private void apply(TenderDraft d) {
        this.agency = Objects.requireNonNull(d.agency(), "agency");
        this.modality = d.modality() == null ? Modality.PREGAO_ELETRONICO : d.modality();
        if (d.uasg() == null && d.processNumber() == null) {
            throw new DomainRuleViolation(
                    "IDENTIFICATION_REQUIRED", "Informe a UASG, o número do processo ou os dois.");
        }
        this.uasg = d.uasg() == null ? null : d.uasg().value();
        this.processNumber =
                d.processNumber() == null ? null : d.processNumber().value();
        this.portal = blankToNull(d.portal());
        this.sessionAt = d.sessionAt();
        this.object = requireText(d.object(), "o objeto (descritivo da licitação)");
        this.materialType = requireText(d.materialType(), "o tipo de material");
        if (d.itemsQuantity() != null && d.itemsQuantity() < 0) {
            throw new DomainRuleViolation("QUANTITY_NEGATIVE", "A quantidade de itens não pode ser negativa.");
        }
        this.itemsQuantity = d.itemsQuantity();
        this.totalValue = Objects.requireNonNull(d.totalValue(), "totalValue").amount();
        this.ownerId = d.ownerId();
        this.notes = blankToNull(d.notes());
    }

    private static void requireNotBefore(LocalDate date, LocalDate reference, String message) {
        if (reference != null && date.isBefore(reference)) {
            throw new DomainRuleViolation("DATE_ORDER", message);
        }
    }

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleViolation("REQUIRED", "Informe " + what + ".");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() {
        return id;
    }

    public Agency getAgency() {
        return agency;
    }

    public Modality getModality() {
        return modality;
    }

    public Uasg getUasg() {
        return Uasg.ofNullable(uasg);
    }

    public ProcessNumber getProcessNumber() {
        return ProcessNumber.ofNullable(processNumber);
    }

    public String getPortal() {
        return portal;
    }

    public OffsetDateTime getSessionAt() {
        return sessionAt;
    }

    public String getObject() {
        return object;
    }

    public String getMaterialType() {
        return materialType;
    }

    public Integer getItemsQuantity() {
        return itemsQuantity;
    }

    public Money getTotalValue() {
        return Money.of(totalValue);
    }

    public TenderStatus getStatus() {
        return status;
    }

    public LocalDate getWonAt() {
        return wonAt;
    }

    public LocalDate getSignedAt() {
        return signedAt;
    }

    public LocalDate getOrdersStartedAt() {
        return ordersStartedAt;
    }

    public LocalDate getClosedAt() {
        return closedAt;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getNotes() {
        return notes;
    }
}
