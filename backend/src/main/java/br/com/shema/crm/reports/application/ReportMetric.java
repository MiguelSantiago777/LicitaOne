package br.com.shema.crm.reports.application;

/** Os quatro números do balanço. Ficam sempre separados: o mesmo negócio passa por vários deles. */
public enum ReportMetric {
    PEDIDOS("Pedidos recebidos", "pedido", "pedidos", "Data do pedido"),
    CONTRATOS("Contratos assinados", "contrato", "contratos", "Data da assinatura"),
    LICITACOES("Licitações vencidas", "licitação", "licitações", "Data da vitória"),
    CARONAS("Caronas", "carona", "caronas", "Data da adesão");

    private final String label;
    private final String singular;
    private final String plural;
    private final String dateBasis;

    ReportMetric(String label, String singular, String plural, String dateBasis) {
        this.label = label;
        this.singular = singular;
        this.plural = plural;
        this.dateBasis = dateBasis;
    }

    public String label() {
        return label;
    }

    public String singular() {
        return singular;
    }

    public String plural() {
        return plural;
    }

    /** Qual data põe o registro no mês. */
    public String dateBasis() {
        return dateBasis;
    }

    /** Título da quebra por tipo: modalidade nas licitações, tipo de contrato em pedidos e contratos. */
    public String kindTitle() {
        return switch (this) {
            case LICITACOES -> "Por modalidade";
            case PEDIDOS, CONTRATOS -> "Por tipo de contrato";
            case CARONAS -> null;
        };
    }
}
