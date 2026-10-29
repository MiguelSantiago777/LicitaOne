package br.com.shema.crm.pricerecords;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** API pública das vigências das atas, para o alerta "ata vencendo" (ADR 0015). */
public interface PriceRecordCalendar {

    record ExpiringRecord(UUID id, UUID tenderId, String number, LocalDate validUntil, boolean allowsAdhesion) {}

    /** Atas com vigência entre as datas (inclusive), das que vencem primeiro. */
    List<ExpiringRecord> validUntilBetween(LocalDate from, LocalDate to);
}
