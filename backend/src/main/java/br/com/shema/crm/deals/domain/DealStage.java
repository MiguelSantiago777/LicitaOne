package br.com.shema.crm.deals.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Etapa do funil. Parte do agregado {@link Pipeline}. O tipo não muda depois de criada. */
@Entity
@Table(name = "deal_stages")
public class DealStage {

    @Id
    private UUID id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private StageKind kind;

    protected DealStage() {}

    DealStage(String name, int position, StageKind kind) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.position = position;
        this.kind = kind;
    }

    void rename(String name) {
        this.name = name;
    }

    void moveTo(int position) {
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getPosition() {
        return position;
    }

    public StageKind getKind() {
        return kind;
    }
}
