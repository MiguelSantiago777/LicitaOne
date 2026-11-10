package br.com.shema.crm.pricerecords.application;

import br.com.shema.crm.pricerecords.PriceRecordSearch;
import br.com.shema.crm.tenders.AgencyRef;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class PriceRecordSearchService implements PriceRecordSearch {

    private final PriceRecordService records;

    PriceRecordSearchService(PriceRecordService records) {
        this.records = records;
    }

    @Override
    public List<Row> search(String filter, String text) {
        PriceRecordService.Filter f = Arrays.stream(PriceRecordService.Filter.values())
                .filter(v -> v.name().equals(filter))
                .findFirst()
                .orElse(PriceRecordService.Filter.VIGENTES);
        return records.list(f, text).stream()
                .map(s -> new Row(
                        s.id(),
                        s.tender() == null ? null : s.tender().id(),
                        s.number(),
                        s.tender() == null ? null : s.tender().agencyName(),
                        s.tender() == null ? null : s.tender().object(),
                        s.validUntil(),
                        s.daysLeft(),
                        s.situation().name(),
                        s.allowsAdhesion(),
                        s.opportunity(),
                        s.agencyCount(),
                        s.referenceValue(),
                        s.usedPercent()))
                .toList();
    }

    @Override
    public List<AgencyRef> prospects(String state, String text) {
        return records.prospects(state, text).stream()
                .map(p -> new AgencyRef(p.id(), p.name(), p.city(), p.state()))
                .toList();
    }
}
