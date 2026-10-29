package br.com.shema.crm.pricerecords.application;

import br.com.shema.crm.pricerecords.PriceRecordCalendar;
import br.com.shema.crm.pricerecords.infrastructure.PriceRecordRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class PriceRecordCalendarService implements PriceRecordCalendar {

    private final PriceRecordRepository records;

    PriceRecordCalendarService(PriceRecordRepository records) {
        this.records = records;
    }

    @Override
    public List<ExpiringRecord> validUntilBetween(LocalDate from, LocalDate to) {
        return records.findByValidUntilBetweenOrderByValidUntilAsc(from, to).stream()
                .map(r -> new ExpiringRecord(
                        r.getId(), r.getTenderId(), r.getNumber(), r.getValidUntil(), r.isAllowsAdhesion()))
                .toList();
    }
}
