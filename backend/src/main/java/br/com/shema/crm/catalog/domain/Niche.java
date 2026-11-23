package br.com.shema.crm.catalog.domain;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Nicho (ramo): limpeza, informática, material de escritório... Cadastrado pela equipe. Arquivado, some dos filtros e
 * da escolha, mas os fornecedores e produtos ligados a ele continuam.
 */
@Entity
@Table(name = "niches")
public class Niche extends AuditableEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "name_key", nullable = false, length = 80)
    private String nameKey;

    @Column(length = 300)
    private String description;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Niche() {}

    public Niche(String name, String description) {
        this.id = UUID.randomUUID();
        describe(name, description);
    }

    public void describe(String name, String description) {
        this.name = CatalogText.required(name, 80, "no nome do nicho", "Informe o nome do nicho.");
        this.nameKey = CatalogText.key(this.name);
        this.description = CatalogText.optional(description, 300, "na descrição do nicho");
    }

    public void archive(Instant at) {
        if (archivedAt != null) {
            throw new DomainRuleViolation("ALREADY_ARCHIVED", "O nicho já está arquivado.");
        }
        this.archivedAt = at;
    }

    public void restore() {
        if (archivedAt == null) {
            throw new DomainRuleViolation("NOT_ARCHIVED", "O nicho não está arquivado.");
        }
        this.archivedAt = null;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNameKey() {
        return nameKey;
    }

    public String getDescription() {
        return description;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
