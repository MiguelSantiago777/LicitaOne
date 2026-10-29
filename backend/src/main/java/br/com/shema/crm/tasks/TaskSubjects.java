package br.com.shema.crm.tasks;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * O que as tarefas precisam saber da negociação ligada. Declarada aqui e implementada pelo módulo de negociações, para
 * a dependência ter um sentido só ({@code deals → tasks}).
 */
public interface TaskSubjects {

    /** @param agencyName órgão da negociação, ou da licitação de origem quando a negociação não tem órgão */
    record DealSubject(UUID id, String title, String agencyName, BigDecimal value, UUID ownerId) {}

    Optional<DealSubject> findDeal(UUID dealId);

    Map<UUID, DealSubject> findDeals(Collection<UUID> dealIds);
}
