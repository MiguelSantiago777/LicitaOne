package br.com.shema.crm.boards.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Um quadro com colunas e cards, montado dentro da transação. É também a resposta da API. */
@Schema(name = "BoardResponse")
public record BoardView(UUID id, String name, long version, boolean canEdit, List<Column> columns) {

    /** @param totalValue soma dos valores das licitações ligadas aos cards da coluna */
    @Schema(name = "BoardColumnResponse")
    public record Column(UUID id, String name, int position, int cardCount, BigDecimal totalValue, List<Card> cards) {}

    @Schema(name = "BoardCardResponse")
    public record Card(
            UUID id,
            UUID columnId,
            int position,
            String title,
            String notes,
            Owner owner,
            LocalDate dueDate,
            boolean overdue,
            TenderLink tender,
            long version) {}

    @Schema(name = "BoardCardOwnerResponse")
    public record Owner(UUID id, String name, String initials) {}

    @Schema(name = "BoardCardTenderResponse")
    public record TenderLink(UUID id, String agencyName, String identification, BigDecimal totalValue, String status) {}
}
