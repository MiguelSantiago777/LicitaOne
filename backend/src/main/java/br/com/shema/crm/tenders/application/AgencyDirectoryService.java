package br.com.shema.crm.tenders.application;

import br.com.shema.crm.tenders.AgencyDirectory;
import br.com.shema.crm.tenders.AgencyRef;
import br.com.shema.crm.tenders.domain.Agency;
import br.com.shema.crm.tenders.domain.AgencyContact;
import br.com.shema.crm.tenders.domain.Sphere;
import br.com.shema.crm.tenders.infrastructure.AgencyContactRepository;
import br.com.shema.crm.tenders.infrastructure.AgencyRepository;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class AgencyDirectoryService implements AgencyDirectory {

    private final AgencyRepository agencies;
    private final AgencyContactRepository contacts;

    AgencyDirectoryService(AgencyRepository agencies, AgencyContactRepository contacts) {
        this.agencies = agencies;
        this.contacts = contacts;
    }

    @Override
    public Optional<AgencyRef> find(UUID agencyId) {
        return agencyId == null ? Optional.empty() : agencies.findById(agencyId).map(AgencyDirectoryService::ref);
    }

    @Override
    public Map<UUID, AgencyRef> findAll(Collection<UUID> agencyIds) {
        if (agencyIds.isEmpty()) {
            // Vazio que aceita get(null): negociação sem órgão é normal
            return Collections.emptyMap();
        }
        return agencies.findAllById(agencyIds).stream()
                .map(AgencyDirectoryService::ref)
                .collect(Collectors.toMap(AgencyRef::id, Function.identity()));
    }

    @Override
    public List<AgencyRef> municipalitiesWithoutWonTender(String state) {
        return agencies.municipalitiesWithoutWonTender(state == null || state.isBlank() ? null : state.trim()).stream()
                .map(AgencyDirectoryService::ref)
                .toList();
    }

    @Override
    public List<String> contactEmails(UUID agencyId) {
        return contacts.findByAgencyIdOrderByPosition(agencyId).stream()
                .map(AgencyContact::getEmail)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    @Override
    public List<String> contactPhones(UUID agencyId) {
        return contacts.findByAgencyIdOrderByPosition(agencyId).stream()
                .map(AgencyContact::getPhone)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    @Override
    public boolean isMunicipal(UUID agencyId) {
        return agencyId != null
                && agencies.findById(agencyId)
                        .map(a -> a.getSphere() == Sphere.MUNICIPAL)
                        .orElse(false);
    }

    private static AgencyRef ref(Agency a) {
        return new AgencyRef(a.getId(), a.getName(), a.getCity(), a.getState());
    }
}
