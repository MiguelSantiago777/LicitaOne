package br.com.shema.crm.tenders.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Ciclo de vida da licitação (ADR 0011).
 *
 * <pre>
 * EM_DISPUTA ──► VENCIDA ──► AGUARDANDO_ASSINATURA ──► CONTRATO_ASSINADO ──► EM_ANDAMENTO ──► ENCERRADO
 *     │             └──────────────────────────────────────►┘        └─────────────────────────►┘
 *     └──► PERDIDA
 * </pre>
 */
public enum TenderStatus {
    EM_DISPUTA("Em disputa", null),
    PERDIDA("Perdida", null),
    VENCIDA("Vencida", DateKind.WON),
    AGUARDANDO_ASSINATURA("Aguardando assinatura", null),
    CONTRATO_ASSINADO("Contrato assinado", DateKind.SIGNED),
    EM_ANDAMENTO("Em andamento", DateKind.ORDERS_STARTED),
    ENCERRADO("Encerrado", DateKind.CLOSED);

    /** A data que a transição para o estado exige. */
    public enum DateKind {
        WON("data da vitória"),
        SIGNED("data de assinatura"),
        ORDERS_STARTED("data do primeiro pedido"),
        CLOSED("data de encerramento");

        private final String label;

        DateKind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final Map<TenderStatus, Set<TenderStatus>> NEXT = Map.of(
            EM_DISPUTA, EnumSet.of(VENCIDA, PERDIDA),
            VENCIDA, EnumSet.of(AGUARDANDO_ASSINATURA, CONTRATO_ASSINADO),
            AGUARDANDO_ASSINATURA, EnumSet.of(CONTRATO_ASSINADO),
            CONTRATO_ASSINADO, EnumSet.of(EM_ANDAMENTO, ENCERRADO),
            EM_ANDAMENTO, EnumSet.of(ENCERRADO),
            PERDIDA, EnumSet.noneOf(TenderStatus.class),
            ENCERRADO, EnumSet.noneOf(TenderStatus.class));

    private final String label;
    private final DateKind requiredDate;

    TenderStatus(String label, DateKind requiredDate) {
        this.label = label;
        this.requiredDate = requiredDate;
    }

    public String label() {
        return label;
    }

    public DateKind requiredDate() {
        return requiredDate;
    }

    public Set<TenderStatus> next() {
        return NEXT.get(this);
    }

    public boolean canMoveTo(TenderStatus target) {
        return NEXT.get(this).contains(target);
    }

    /** Estados em que se pode cadastrar uma licitação. */
    public boolean isInitial() {
        return this == EM_DISPUTA || this == VENCIDA;
    }
}
