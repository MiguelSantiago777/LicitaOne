package br.com.shema.crm.tasks.application;

import br.com.shema.crm.tasks.domain.TaskOrigin;
import br.com.shema.crm.tasks.domain.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Uma tarefa pronta para a lista. É também a resposta da API.
 *
 * @param value valor da negociação ligada; sem negociação, o da licitação
 * @param canEdit se quem pediu pode alterar, concluir ou apagar
 */
@Schema(name = "TaskResponse")
public record TaskView(
        UUID id,
        String title,
        String notes,
        OffsetDateTime dueAt,
        TaskStatus status,
        Owner owner,
        DealLink deal,
        TenderLink tender,
        BigDecimal value,
        TaskOrigin origin,
        OffsetDateTime completedAt,
        long version,
        boolean canEdit) {

    @Schema(name = "TaskOwnerResponse")
    public record Owner(UUID id, String name, String initials) {}

    @Schema(name = "TaskDealResponse")
    public record DealLink(UUID id, String title, String agencyName, BigDecimal value) {}

    @Schema(name = "TaskTenderResponse")
    public record TenderLink(UUID id, String agencyName, String identification, BigDecimal totalValue) {}
}
