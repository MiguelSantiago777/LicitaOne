package br.com.shema.crm.deals.domain;

/** Tipo da etapa: é ele que decide o status da negociação que está nela. */
public enum StageKind {
    ABERTA,
    GANHA,
    PERDIDA;

    public DealStatus status() {
        return switch (this) {
            case ABERTA -> DealStatus.EM_ANDAMENTO;
            case GANHA -> DealStatus.GANHA;
            case PERDIDA -> DealStatus.PERDIDA;
        };
    }
}
