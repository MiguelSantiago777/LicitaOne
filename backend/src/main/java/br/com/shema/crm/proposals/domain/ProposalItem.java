package br.com.shema.crm.proposals.domain;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.domain.Quantities;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** Item da proposta. Parte do agregado {@link Proposal}; pode ficar incompleto até a hora do PDF. */
@Entity
@Table(name = "proposal_items")
public class ProposalItem {

    @Id
    private UUID id;

    @Column(nullable = false)
    private int position;

    @Column(name = "item_number", length = 20)
    private String itemNumber;

    @Column(length = 2000)
    private String description;

    @Column(length = 20)
    private String unit;

    @Column(length = 100)
    private String brand;

    @Column(precision = 15, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", precision = 15, scale = 4)
    private BigDecimal unitPrice;

    protected ProposalItem() {}

    ProposalItem(int position, ProposalItemDraft draft) {
        this.id = UUID.randomUUID();
        this.position = position;
        this.itemNumber = Proposal.text(draft.itemNumber(), 20, "no número do item");
        this.description = Proposal.text(draft.description(), 2000, "na descrição do item");
        this.unit = Proposal.text(draft.unit(), 20, "na unidade");
        this.brand = Proposal.text(draft.brand(), 100, "na marca");
        this.quantity = draft.quantity() == null ? null : Quantities.quantity(draft.quantity(), "do item");
        this.unitPrice = draft.unitPrice() == null ? null : Quantities.unitPrice(draft.unitPrice());
    }

    /** Copia para outra proposta (duplicar), com id novo. */
    ProposalItem copy() {
        return new ProposalItem(position, draft());
    }

    ProposalItemDraft draft() {
        return new ProposalItemDraft(itemNumber, description, unit, brand, quantity, unitPrice);
    }

    /** Quantidade vezes valor unitário, em centavos; nulo enquanto falta um dos dois. */
    public BigDecimal total() {
        return quantity == null || unitPrice == null ? null : Quantities.total(quantity, unitPrice);
    }

    public boolean isComplete() {
        return description != null && quantity != null && unitPrice != null;
    }

    /** O número que aparece no documento: o digitado ou, se em branco, a posição. */
    public String displayNumber() {
        return itemNumber != null ? itemNumber : String.valueOf(position);
    }

    static void requireNotTooMany(int size) {
        if (size > Proposal.MAX_ITEMS) {
            throw new DomainRuleViolation(
                    "TOO_MANY_ITEMS", "Uma proposta tem no máximo " + Proposal.MAX_ITEMS + " itens.");
        }
    }

    public UUID getId() {
        return id;
    }

    public int getPosition() {
        return position;
    }

    public String getItemNumber() {
        return itemNumber;
    }

    public String getDescription() {
        return description;
    }

    public String getUnit() {
        return unit;
    }

    public String getBrand() {
        return brand;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}
