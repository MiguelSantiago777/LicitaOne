package br.com.shema.crm.tenders.application;

import br.com.shema.crm.shared.security.CurrentUser;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import br.com.shema.crm.tenders.domain.Agency;
import br.com.shema.crm.tenders.domain.Sphere;
import br.com.shema.crm.tenders.domain.Uasg;
import br.com.shema.crm.tenders.infrastructure.AgencyRepository;
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

    private final AgencyRepository agencies;

    public AgencyService(AgencyRepository agencies) {
        this.agencies = agencies;
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
}
