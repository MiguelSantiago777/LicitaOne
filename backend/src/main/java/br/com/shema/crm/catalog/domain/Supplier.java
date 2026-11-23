package br.com.shema.crm.catalog.domain;

import br.com.shema.crm.shared.domain.BrDocuments;
import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

/**
 * Fornecedor: dados da empresa, contatos e os nichos que atende. Arquivado, sai das listas e da escolha nos produtos,
 * mas os preços que já tem continuam guardados.
 */
@Entity
@Table(name = "suppliers")
public class Supplier extends AuditableEntity {

    public static final int MAX_CONTACTS = 20;
    public static final int MAX_NICHES = 50;

    @Id
    private UUID id;

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Column(name = "trade_name", length = 200)
    private String tradeName;

    @Column(length = 14)
    private String cnpj;

    @Column(length = 100)
    private String city;

    @Column(length = 2)
    private String state;

    @Column(length = 200)
    private String website;

    @Column(length = 2000)
    private String notes;

    @Column(name = "search_text", nullable = false, length = 600)
    private String searchText;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @ElementCollection
    @CollectionTable(name = "supplier_niches", joinColumns = @JoinColumn(name = "supplier_id"))
    @Column(name = "niche_id", nullable = false)
    @BatchSize(size = 100)
    private Set<UUID> nicheIds = new LinkedHashSet<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "supplier_id", nullable = false)
    @OrderBy("position")
    @BatchSize(size = 100)
    private List<SupplierContact> contacts = new ArrayList<>();

    protected Supplier() {}

    /** Fornecedor novo: só a razão social é obrigatória; o resto a pessoa completa na tela. */
    public Supplier(SupplierDraft draft) {
        this.id = UUID.randomUUID();
        update(draft);
    }

    /**
     * Troca tudo pelo que veio. Os nichos já foram conferidos pelo serviço (existem e, os novos, não estão arquivados).
     * Contato todo em branco é ignorado.
     */
    public void update(SupplierDraft d) {
        this.legalName =
                CatalogText.required(d.legalName(), 200, "na razão social", "Informe a razão social do fornecedor.");
        this.tradeName = CatalogText.optional(d.tradeName(), 200, "no nome fantasia");
        this.cnpj = BrDocuments.cnpj(d.cnpj());
        this.city = CatalogText.optional(d.city(), 100, "na cidade");
        this.state = CatalogText.state(d.state());
        this.website = CatalogText.optional(d.website(), 200, "no site");
        this.notes = CatalogText.optional(d.notes(), 2000, "nas observações");
        List<UUID> niches = d.nicheIds() == null ? List.of() : d.nicheIds();
        if (niches.size() > MAX_NICHES) {
            throw new DomainRuleViolation("TOO_MANY_NICHES", "Um fornecedor tem no máximo " + MAX_NICHES + " nichos.");
        }
        Set<UUID> wanted = new LinkedHashSet<>(niches);
        nicheIds.retainAll(wanted);
        nicheIds.addAll(wanted);
        List<SupplierDraft.Contact> drafts = d.contacts() == null ? List.of() : d.contacts();
        if (drafts.size() > MAX_CONTACTS) {
            throw new DomainRuleViolation(
                    "TOO_MANY_CONTACTS", "Um fornecedor tem no máximo " + MAX_CONTACTS + " contatos.");
        }
        contacts.clear();
        for (SupplierDraft.Contact draft : drafts) {
            SupplierContact contact = new SupplierContact(contacts.size() + 1, draft);
            if (!contact.isEmpty()) {
                contacts.add(contact);
            }
        }
        this.searchText = CatalogText.searchText(600, legalName, tradeName, cnpj, city);
    }

    public void archive(Instant at) {
        if (archivedAt != null) {
            throw new DomainRuleViolation("ALREADY_ARCHIVED", "O fornecedor já está arquivado.");
        }
        this.archivedAt = at;
    }

    public void restore() {
        if (archivedAt == null) {
            throw new DomainRuleViolation("NOT_ARCHIVED", "O fornecedor não está arquivado.");
        }
        this.archivedAt = null;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    /** O nome que a equipe usa: o fantasia, se tiver, senão a razão social. */
    public String displayName() {
        return tradeName != null ? tradeName : legalName;
    }

    public UUID getId() {
        return id;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getTradeName() {
        return tradeName;
    }

    public String getCnpj() {
        return cnpj;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getWebsite() {
        return website;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public Set<UUID> getNicheIds() {
        return Set.copyOf(nicheIds);
    }

    public List<SupplierContact> getContacts() {
        return List.copyOf(contacts);
    }
}
