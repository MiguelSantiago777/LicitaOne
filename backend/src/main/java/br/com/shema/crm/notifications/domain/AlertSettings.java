package br.com.shema.crm.notifications.domain;

import java.util.Comparator;
import java.util.List;

/**
 * Prazos das regras, configuráveis (seção 11: prazo que depende de edital ou de combinação com o cliente não fica fixo
 * no código).
 *
 * @param priceRecordExpiringDays faixas de aviso antes do vencimento da ata, ex.: 90, 60, 30
 */
public record AlertSettings(
        int contractWithoutOrderDays, List<Integer> priceRecordExpiringDays, int tenderWithoutContractDays) {

    public AlertSettings {
        if (contractWithoutOrderDays < 1 || tenderWithoutContractDays < 1) {
            throw new IllegalArgumentException("Os prazos dos alertas precisam ser de pelo menos 1 dia");
        }
        if (priceRecordExpiringDays == null
                || priceRecordExpiringDays.isEmpty()
                || priceRecordExpiringDays.stream().anyMatch(d -> d == null || d < 1)) {
            throw new IllegalArgumentException("Informe ao menos uma faixa de aviso de ata, com 1 dia ou mais");
        }
        priceRecordExpiringDays = priceRecordExpiringDays.stream()
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    /** A maior faixa: a busca de atas olha até esse número de dias à frente. */
    public int widestExpiringBand() {
        return priceRecordExpiringDays.getFirst();
    }
}
