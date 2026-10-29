package br.com.shema.crm.deals.application;

import br.com.shema.crm.deals.domain.DealStatus;
import java.util.Set;
import java.util.UUID;

/**
 * Filtros do funil. Tudo opcional.
 *
 * @param q busca no título, no órgão e na identificação da licitação
 * @param ownerId só as negociações deste responsável ("Minhas negociações" manda o usuário atual)
 */
public record DealFilter(String q, Set<DealStatus> statuses, UUID ownerId, UUID agencyId, UUID tenderId) {

    public static final DealFilter NONE = new DealFilter(null, null, null, null, null);
}
