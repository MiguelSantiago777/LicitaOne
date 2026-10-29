package br.com.shema.crm.deals.application;

import br.com.shema.crm.deals.domain.Deal;
import br.com.shema.crm.deals.infrastructure.DealRepository;
import br.com.shema.crm.tasks.TaskSubjects;
import br.com.shema.crm.tenders.AgencyDirectory;
import br.com.shema.crm.tenders.AgencyRef;
import br.com.shema.crm.tenders.TenderLifecycle;
import br.com.shema.crm.tenders.TenderRef;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** O que a lista de tarefas mostra da negociação ligada (o módulo de tarefas declara, este implementa). */
@Service
@Transactional(readOnly = true)
class DealTaskSubjects implements TaskSubjects {

    private final DealRepository deals;
    private final AgencyDirectory agencies;
    private final TenderLifecycle tenders;

    DealTaskSubjects(DealRepository deals, AgencyDirectory agencies, TenderLifecycle tenders) {
        this.deals = deals;
        this.agencies = agencies;
        this.tenders = tenders;
    }

    @Override
    public Optional<DealSubject> findDeal(UUID dealId) {
        return dealId == null
                ? Optional.empty()
                : deals.findById(dealId).map(d -> subjects(List.of(d)).get(d.getId()));
    }

    @Override
    public Map<UUID, DealSubject> findDeals(Collection<UUID> dealIds) {
        if (dealIds.isEmpty()) {
            // Vazio que aceita get(null): tarefa sem negociação é normal
            return Collections.emptyMap();
        }
        return subjects(deals.findAllById(dealIds));
    }

    private Map<UUID, DealSubject> subjects(List<Deal> list) {
        Map<UUID, AgencyRef> linkedAgencies = agencies.findAll(ids(list, Deal::getAgencyId));
        Map<UUID, TenderRef> linkedTenders = tenders.findAll(ids(list, Deal::getTenderId));
        return list.stream()
                .collect(Collectors.toMap(
                        Deal::getId,
                        d -> new DealSubject(
                                d.getId(),
                                d.getTitle(),
                                agencyName(d, linkedAgencies, linkedTenders),
                                d.getValue(),
                                d.getOwnerId())));
    }

    private static String agencyName(Deal d, Map<UUID, AgencyRef> agencies, Map<UUID, TenderRef> tenders) {
        if (d.getAgencyId() != null && agencies.containsKey(d.getAgencyId())) {
            return agencies.get(d.getAgencyId()).name();
        }
        if (d.getTenderId() != null && tenders.containsKey(d.getTenderId())) {
            return tenders.get(d.getTenderId()).agencyName();
        }
        return null;
    }

    private static Set<UUID> ids(List<Deal> list, Function<Deal, UUID> id) {
        return list.stream().map(id).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
