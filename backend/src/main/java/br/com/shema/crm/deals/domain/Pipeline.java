package br.com.shema.crm.deals.domain;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Funil de vendas com as etapas na ordem. A versão protege a estrutura (duas pessoas reordenando ao mesmo tempo
 * recebem aviso). Sempre sobra pelo menos uma etapa de cada tipo: sem etapa "ganha" ou "perdida" não há como fechar
 * uma negociação.
 */
@Entity
@Table(name = "deal_pipelines")
public class Pipeline extends AuditableEntity {

    public static final int MAX_STAGES = 15;

    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String name;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "pipeline_id", nullable = false)
    @OrderBy("position")
    private List<DealStage> stages = new ArrayList<>();

    protected Pipeline() {}

    public Pipeline(String name) {
        this.id = UUID.randomUUID();
        this.name = text(name, "o nome do funil", 80);
    }

    public DealStage addStage(String name, StageKind kind) {
        if (stages.size() >= MAX_STAGES) {
            throw new DomainRuleViolation("TOO_MANY_STAGES", "O funil tem no máximo " + MAX_STAGES + " etapas.");
        }
        DealStage stage = new DealStage(text(name, "o nome da etapa", 60), stages.size(), kind);
        stages.add(stage);
        return stage;
    }

    public void renameStage(UUID stageId, String name) {
        stage(stageId).rename(text(name, "o nome da etapa", 60));
    }

    /** Leva a etapa para a posição (começa em 0) e renumera as outras. */
    public void moveStage(UUID stageId, int position) {
        DealStage stage = stage(stageId);
        stages.remove(stage);
        stages.add(Math.clamp(position, 0, stages.size()), stage);
        renumber();
    }

    /** Só sai etapa vazia, e nunca a última do seu tipo. */
    public void removeStage(UUID stageId, long dealsInStage) {
        DealStage stage = stage(stageId);
        if (dealsInStage > 0) {
            throw new DomainRuleViolation(
                    "STAGE_NOT_EMPTY",
                    "A etapa \"" + stage.getName() + "\" tem " + dealsInStage
                            + (dealsInStage == 1 ? " negociação" : " negociações") + ". Mova antes de excluir.");
        }
        if (stages.stream().filter(s -> s.getKind() == stage.getKind()).count() == 1) {
            throw new DomainRuleViolation(
                    "LAST_STAGE_OF_KIND",
                    "O funil precisa de pelo menos uma etapa " + kindLabel(stage.getKind()) + ".");
        }
        stages.remove(stage);
        renumber();
    }

    public DealStage stage(UUID stageId) {
        return stages.stream()
                .filter(s -> s.getId().equals(stageId))
                .findFirst()
                .orElseThrow(() -> new DomainRuleViolation("STAGE_NOT_FOUND", "Etapa não encontrada no funil."));
    }

    /** Onde entra uma negociação nova quando ninguém escolhe: a primeira etapa aberta. */
    public DealStage firstOpenStage() {
        return stages.stream()
                .filter(s -> s.getKind() == StageKind.ABERTA)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Funil sem etapa aberta"));
    }

    private void renumber() {
        for (int i = 0; i < stages.size(); i++) {
            stages.get(i).moveTo(i);
        }
    }

    private static String kindLabel(StageKind kind) {
        return switch (kind) {
            case ABERTA -> "em andamento";
            case GANHA -> "de negociação ganha";
            case PERDIDA -> "de negociação perdida";
        };
    }

    static String text(String value, String what, int max) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleViolation("REQUIRED", "Informe " + what + ".");
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        if (trimmed.length() > max) {
            throw new DomainRuleViolation("TOO_LONG", "Use no máximo " + max + " caracteres para " + what + ".");
        }
        return trimmed;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<DealStage> getStages() {
        return List.copyOf(stages);
    }
}
