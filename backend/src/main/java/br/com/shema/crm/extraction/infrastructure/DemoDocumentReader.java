package br.com.shema.crm.extraction.infrastructure;

import br.com.shema.crm.extraction.application.DocumentReader;
import br.com.shema.crm.extraction.domain.RawReading;
import br.com.shema.crm.extraction.domain.RawReading.RawField;
import br.com.shema.crm.extraction.domain.RawReading.RawItem;
import java.util.List;

/**
 * Leitor de demonstração: não lê o PDF nem chama a IA, devolve sempre a mesma ata. Existe para o E2E rodar o fluxo
 * inteiro no CI sem chave e sem custo ({@code SHEMA_EXTRACTION_DEMO=true}). Nunca é ligado em produção.
 */
class DemoDocumentReader implements DocumentReader {

    static final String MODEL = "demonstração (sem IA)";

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Reading read(ReadRequest request) {
        RawReading raw = new RawReading(
                "ATA",
                new RawField("ARP 045/2026", 1),
                new RawField("2026-06-02", 1),
                new RawField("2026-06-02", 1),
                new RawField("2027-06-01", 2),
                new RawField("SIM", 3),
                new RawField("85250.00", 2),
                new RawField("Prefeitura Municipal de Conceição de Macabu", 1),
                new RawField("Shema Suprimentos e Serviços Ltda", 1),
                List.of(
                        new RawItem("1", "Banner em lona 440g com acabamento em bastão", "M²", "1200", "42.50", 4),
                        new RawItem("1", "Adesivo perfurado para vidro", "M²", "800", "38.75", 4),
                        new RawItem("2", "Faixa em lona 3 x 0,70 m", "UN", "", "65.00", 5)),
                List.of("A quantidade da faixa (item 3) está cortada na página 5."));
        return new Reading(raw, MODEL, 0, 0);
    }
}
