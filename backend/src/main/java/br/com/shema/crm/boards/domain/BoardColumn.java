package br.com.shema.crm.boards.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Coluna de um quadro. Parte do agregado {@link Board}. */
@Entity
@Table(name = "board_columns")
public class BoardColumn {

    @Id
    private UUID id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private int position;

    protected BoardColumn() {}

    BoardColumn(String name, int position) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.position = position;
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
}
