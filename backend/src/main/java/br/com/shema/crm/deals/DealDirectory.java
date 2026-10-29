package br.com.shema.crm.deals;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** API pública das negociações: a carona aponta para a negociação que a originou. */
public interface DealDirectory {

    Optional<DealRef> find(UUID dealId);

    /** Busca em lote, para listas não fazerem uma consulta por linha. */
    Map<UUID, DealRef> findAll(Collection<UUID> dealIds);
}
