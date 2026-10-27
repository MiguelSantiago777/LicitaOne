package br.com.shema.crm.tenders.domain;

import br.com.shema.crm.shared.domain.Money;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Os dados editáveis de uma licitação, já convertidos para tipos do domínio. */
public record TenderDraft(
        Agency agency,
        Modality modality,
        Uasg uasg,
        ProcessNumber processNumber,
        String portal,
        OffsetDateTime sessionAt,
        String object,
        String materialType,
        Integer itemsQuantity,
        Money totalValue,
        UUID ownerId,
        String notes) {}
