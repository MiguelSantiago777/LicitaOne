package br.com.shema.crm.contracts.application;

import br.com.shema.crm.contracts.ContractFollowUp;
import br.com.shema.crm.contracts.infrastructure.ContractTotalsQuery;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class ContractFollowUpService implements ContractFollowUp {

    private final ContractTotalsQuery query;

    ContractFollowUpService(ContractTotalsQuery query) {
        this.query = query;
    }

    @Override
    public List<ContractWithoutOrder> withoutOrderSignedOnOrBefore(LocalDate signedOnOrBefore) {
        return query.withoutOrder(signedOnOrBefore);
    }
}
