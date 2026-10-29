package br.com.shema.crm.deals.application;

import br.com.shema.crm.deals.DealDirectory;
import br.com.shema.crm.deals.DealRef;
import br.com.shema.crm.deals.domain.Deal;
import br.com.shema.crm.deals.infrastructure.DealRepository;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class DealDirectoryService implements DealDirectory {

    private final DealRepository deals;

    DealDirectoryService(DealRepository deals) {
        this.deals = deals;
    }

    @Override
    public Optional<DealRef> find(UUID dealId) {
        return dealId == null ? Optional.empty() : deals.findById(dealId).map(DealDirectoryService::ref);
    }

    @Override
    public Map<UUID, DealRef> findAll(Collection<UUID> dealIds) {
        if (dealIds.isEmpty()) {
            // Vazio que aceita get(null): carona sem negociação é normal
            return Collections.emptyMap();
        }
        return deals.findAllById(dealIds).stream()
                .map(DealDirectoryService::ref)
                .collect(Collectors.toMap(DealRef::id, Function.identity()));
    }

    private static DealRef ref(Deal d) {
        return new DealRef(
                d.getId(), d.getTitle(), d.getStatus().name(), d.getAgencyId(), d.getTenderId(), d.getValue());
    }
}
