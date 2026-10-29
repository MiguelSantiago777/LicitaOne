package br.com.shema.crm.deals.application;

import br.com.shema.crm.deals.domain.DealStatus;
import br.com.shema.crm.deals.domain.StageKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * O funil com as etapas e as negociações que passaram no filtro. Contador e soma de cada etapa seguem o filtro. É
 * também a resposta da API.
 *
 * @param canEditStages só ADMIN mexe nas etapas
 * @param canCreate ADMIN e COMERCIAL criam negociações
 */
@Schema(name = "PipelineResponse")
public record PipelineView(
        UUID id,
        String name,
        long version,
        boolean canEditStages,
        boolean canCreate,
        int dealCount,
        BigDecimal totalValue,
        List<Stage> stages) {

    @Schema(name = "PipelineStageResponse")
    public record Stage(
            UUID id,
            String name,
            int position,
            StageKind kind,
            int dealCount,
            BigDecimal totalValue,
            List<Deal> deals) {}

    /**
     * @param isNew criada há menos de 7 dias e ainda em andamento
     * @param overdueTask a tarefa atrasada mais antiga, para a faixa do card
     */
    @Schema(name = "DealResponse")
    public record Deal(
            UUID id,
            String title,
            UUID stageId,
            String stageName,
            int position,
            DealStatus status,
            boolean isNew,
            BigDecimal value,
            int priority,
            Owner owner,
            AgencyLink agency,
            TenderLink tender,
            String notes,
            int openTasks,
            OverdueTask overdueTask,
            LocalDate closedAt,
            long version,
            boolean canEdit) {}

    @Schema(name = "DealOwnerResponse")
    public record Owner(UUID id, String name, String initials) {}

    @Schema(name = "DealAgencyResponse")
    public record AgencyLink(UUID id, String name, String city, String state) {}

    @Schema(name = "DealTenderResponse")
    public record TenderLink(UUID id, String agencyName, String identification, BigDecimal totalValue, String status) {}

    @Schema(name = "DealOverdueTaskResponse")
    public record OverdueTask(String title, OffsetDateTime dueAt, int overdueCount) {}
}
