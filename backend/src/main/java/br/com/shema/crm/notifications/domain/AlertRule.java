package br.com.shema.crm.notifications.domain;

/**
 * Regras de alerta da seção 5.4. Os nomes dos eventos da especificação (ContratoSemPedido30Dias, AtaVencendo,
 * LicitacaoVencidaSemContrato) são estas regras, no campo {@code rule} do evento {@code alert.raised}.
 */
public enum AlertRule {
    CONTRATO_SEM_PEDIDO("Contrato sem pedido"),
    ATA_VENCENDO("Ata vencendo"),
    LICITACAO_SEM_CONTRATO("Vencida sem contrato");

    private final String label;

    AlertRule(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Chave de idempotência: regra + assunto + período (quando a regra tem período). */
    public String key(Object subject, Object... period) {
        StringBuilder key = new StringBuilder(name()).append(':').append(subject);
        for (Object p : period) {
            key.append(':').append(p);
        }
        return key.toString();
    }
}
