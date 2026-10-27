package br.com.shema.crm.identity;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** API pública do módulo de identidade para os outros módulos. */
public interface UserDirectory {

    Optional<UserSummary> find(UUID id);

    /** Busca em lote, para listas não fazerem uma consulta por linha. */
    Map<UUID, UserSummary> findAll(Collection<UUID> ids);
}
