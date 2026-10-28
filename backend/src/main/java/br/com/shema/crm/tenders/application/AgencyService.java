package br.com.shema.crm.tenders.application;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.security.CurrentUser;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import br.com.shema.crm.tenders.domain.Agency;
import br.com.shema.crm.tenders.domain.AgencyContact;
import br.com.shema.crm.tenders.domain.Sphere;
import br.com.shema.crm.tenders.domain.Uasg;
import br.com.shema.crm.tenders.infrastructure.AgencyContactRepository;
import br.com.shema.crm.tenders.infrastructure.AgencyRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AgencyService {

    public record AgencyInput(
            String name, Sphere sphere, String city, String state, String cnpj, String defaultUasg, String notes) {}

    public record ContactInput(String name, String role, String email, String phone, String notes) {}

    private final AgencyRepository agencies;
    private final AgencyContactRepository contacts;
    private final EntityManager entityManager;

    public AgencyService(AgencyRepository agencies, AgencyContactRepository contacts, EntityManager entityManager) {
        this.agencies = agencies;
        this.contacts = contacts;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public Page<Agency> search(String q, Pageable pageable) {
        return agencies.search(q == null || q.isBlank() ? null : q.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public Agency get(UUID id) {
        return agencies.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Órgão não encontrado."));
    }

    public Agency create(AgencyInput in) {
        CurrentUser.get().requireCanEditOwnedBy(null);
        if (in.name() != null
                && in.city() != null
                && agencies.existsByNameIgnoreCaseAndCityIgnoreCase(
                        in.name().trim(), in.city().trim())) {
            throw new ApiException(ErrorCode.CONFLICT, "Já existe um órgão com este nome neste município.");
        }
        return agencies.save(new Agency(
                in.name(),
                in.sphere(),
                in.city(),
                in.state(),
                in.cnpj(),
                Uasg.ofNullable(in.defaultUasg()),
                in.notes()));
    }

    public Agency update(UUID id, long expectedVersion, AgencyInput in) {
        CurrentUser.get().requireCanEditOwnedBy(null);
        Agency agency = get(id);
        if (agency.getVersion() == null || agency.getVersion() != expectedVersion) {
            throw new ApiException(
                    ErrorCode.VERSION_CONFLICT, "Outra pessoa alterou este órgão enquanto você editava. Recarregue.");
        }
        agency.update(
                in.name(),
                in.sphere(),
                in.city(),
                in.state(),
                in.cnpj(),
                Uasg.ofNullable(in.defaultUasg()),
                in.notes());
        agencies.flush();
        return agency;
    }

    @Transactional(readOnly = true)
    public List<AgencyContact> contacts(UUID agencyId) {
        get(agencyId);
        return contacts.findByAgencyIdOrderByPosition(agencyId);
    }

    /**
     * Substitui a lista de contatos. Usa a versão do órgão: duas pessoas editando contatos ao mesmo tempo não
     * apagam o trabalho uma da outra sem aviso.
     */
    public Agency replaceContacts(UUID agencyId, long expectedVersion, List<ContactInput> inputs) {
        CurrentUser.get().requireCanEditOwnedBy(null);
        Agency agency = get(agencyId);
        if (agency.getVersion() == null || agency.getVersion() != expectedVersion) {
            throw new ApiException(
                    ErrorCode.VERSION_CONFLICT, "Outra pessoa alterou este órgão enquanto você editava. Recarregue.");
        }
        if (inputs.size() > AgencyContact.MAX_PER_AGENCY) {
            throw new DomainRuleViolation(
                    "TOO_MANY_CONTACTS", "Um órgão tem no máximo " + AgencyContact.MAX_PER_AGENCY + " contatos.");
        }
        entityManager.lock(agency, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        List<AgencyContact> replacement = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            ContactInput in = inputs.get(i);
            replacement.add(new AgencyContact(agencyId, i, in.name(), in.role(), in.email(), in.phone(), in.notes()));
        }
        contacts.deleteByAgencyId(agencyId);
        contacts.saveAll(replacement);
        contacts.flush();
        return agency;
    }
}
