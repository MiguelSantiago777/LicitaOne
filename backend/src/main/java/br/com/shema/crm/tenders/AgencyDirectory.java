package br.com.shema.crm.tenders;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** API pública dos órgãos para os outros módulos (negociações apontam para o órgão prospectado). */
public interface AgencyDirectory {

    Optional<AgencyRef> find(UUID agencyId);

    /** Busca em lote, para listas não fazerem uma consulta por linha. */
    Map<UUID, AgencyRef> findAll(Collection<UUID> agencyIds);
}
