package br.com.shema.crm.pricerecords.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Datas nas mensagens de regra, como o usuário lê na tela (dd/MM/yyyy). */
final class BrDates {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private BrDates() {}

    static String format(LocalDate date) {
        return date.format(FORMAT);
    }
}
