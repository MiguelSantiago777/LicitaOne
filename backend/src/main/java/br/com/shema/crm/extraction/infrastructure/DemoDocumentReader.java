package br.com.shema.crm.extraction.infrastructure;

import br.com.shema.crm.extraction.application.DocumentReader;
import br.com.shema.crm.extraction.domain.Extraction;
import br.com.shema.crm.extraction.domain.RawOrder;
import br.com.shema.crm.extraction.domain.RawOrder.RawOrderLine;
import br.com.shema.crm.extraction.domain.RawReading;
import br.com.shema.crm.extraction.domain.RawReading.RawField;
import br.com.shema.crm.extraction.domain.RawReading.RawItem;
import java.time.LocalDate;
import java.util.List;

/**
 * Leitor de demonstração: não lê o PDF nem chama a IA. Devolve sempre a mesma ata, o mesmo contrato e, para pedidos,
 * um pedido com os dois primeiros itens do contrato e uma linha que não é dele. Existe para o E2E rodar o fluxo
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
        if (request.kind() == Extraction.Kind.CONTRATO) {
            return new Reading(contract(LocalDate.now()), MODEL, 0, 0);
        }
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

    /**
     * Contrato assinado há 10 dias e válido por um ano, para caber depois da vitória de qualquer licitação criada no
     * E2E. Os dois itens somam o valor total (R$ 48.000,00).
     */
    private static RawReading contract(LocalDate today) {
        return new RawReading(
                "CONTRATO",
                new RawField("CT 031/2026", 1),
                new RawField(today.minusDays(10).toString(), 1),
                new RawField(today.minusDays(10).toString(), 1),
                new RawField(today.plusYears(1).toString(), 2),
                new RawField("", 0),
                new RawField("48000.00", 1),
                new RawField("Prefeitura Municipal de Petrópolis", 1),
                new RawField("Shema Suprimentos e Serviços Ltda", 1),
                List.of(
                        new RawItem("", "Faixa em lona 3 x 0,70 m", "UN", "400", "60.00", 3),
                        new RawItem("", "Banner em lona 440g", "M²", "800", "30.00", 3)),
                List.of());
    }

    @Override
    public OrderRead readOrder(OrderReadRequest request) {
        RawOrder raw = new RawOrder(
                request.isText() ? "MENSAGEM" : "AUTORIZACAO_FORNECIMENTO",
                new RawField("", 0),
                new RawField("AF 077/2026", request.isText() ? 0 : 1),
                new RawField("", 0),
                new RawField("", 0),
                new RawField("Entregar na Secretaria de Educação em até 10 dias", request.isText() ? 0 : 1),
                List.of(
                        new RawOrderLine("1", "Item 1 do contrato", "", "10", "", 1, "10 unidades do item 1"),
                        new RawOrderLine("2", "Item 2 do contrato", "", "5", "", 1, "5 unidades do item 2"),
                        new RawOrderLine("", "Cartucho de toner 85A", "UN", "3", "120.00", 1, "3 toners 85A")),
                List.of("A assinatura do ofício está ilegível."));
        return new OrderRead(raw, MODEL, 0, 0);
    }
}
