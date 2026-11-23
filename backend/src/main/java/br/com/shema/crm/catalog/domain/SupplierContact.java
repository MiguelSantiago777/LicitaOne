package br.com.shema.crm.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Pessoa de contato no fornecedor. Parte do agregado {@link Supplier}. */
@Entity
@Table(name = "supplier_contacts")
public class SupplierContact {

    @Id
    private UUID id;

    @Column(nullable = false)
    private int position;

    @Column(length = 120)
    private String name;

    @Column(length = 40)
    private String phone;

    @Column(length = 254)
    private String email;

    protected SupplierContact() {}

    SupplierContact(int position, SupplierDraft.Contact draft) {
        this.id = UUID.randomUUID();
        this.position = position;
        this.name = CatalogText.optional(draft.name(), 120, "no nome do contato");
        this.phone = CatalogText.optional(draft.phone(), 40, "no telefone");
        this.email = CatalogText.optional(draft.email(), 254, "no e-mail");
    }

    boolean isEmpty() {
        return name == null && phone == null && email == null;
    }

    public UUID getId() {
        return id;
    }

    public int getPosition() {
        return position;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }
}
