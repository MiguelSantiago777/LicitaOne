import type { TenderResponseStatus } from '../../api/generated/model';

export type Status = TenderResponseStatus;

/** Abas da tela de licitações: cada uma agrupa um ou mais estados do ciclo de vida. */
export interface TenderTab {
  id: string;
  label: string;
  statuses: Status[];
}

export const TENDER_TABS: TenderTab[] = [
  { id: 'disputa', label: 'Em disputa', statuses: ['EM_DISPUTA'] },
  { id: 'vencidas', label: 'Vencidas', statuses: ['VENCIDA', 'AGUARDANDO_ASSINATURA'] },
  { id: 'assinados', label: 'Contratos assinados', statuses: ['CONTRATO_ASSINADO'] },
  { id: 'andamento', label: 'Em andamento', statuses: ['EM_ANDAMENTO'] },
  { id: 'encerrados', label: 'Encerrados', statuses: ['ENCERRADO'] },
  { id: 'perdidas', label: 'Perdidas', statuses: ['PERDIDA'] },
];

export const DEFAULT_TAB = 'vencidas';

const TONES: Record<Status, string> = {
  EM_DISPUTA: 'violet',
  PERDIDA: 'red',
  VENCIDA: 'amber',
  AGUARDANDO_ASSINATURA: 'amber',
  CONTRATO_ASSINADO: 'blue',
  EM_ANDAMENTO: 'green',
  ENCERRADO: '',
};

export function statusTone(status: Status): string {
  return TONES[status];
}

/** Rótulo do botão de ação que leva a cada estado. */
export const ACTION_LABELS: Record<Status, string> = {
  EM_DISPUTA: 'Voltar para disputa',
  VENCIDA: 'Marcar como vencida',
  PERDIDA: 'Marcar como perdida',
  AGUARDANDO_ASSINATURA: 'Contrato recebido para assinar',
  CONTRATO_ASSINADO: 'Registrar assinatura',
  EM_ANDAMENTO: 'Registrar primeiro pedido',
  ENCERRADO: 'Encerrar contrato',
};

export const MODALITIES = [
  { value: 'PREGAO_ELETRONICO', label: 'Pregão eletrônico' },
  { value: 'PREGAO_PRESENCIAL', label: 'Pregão presencial' },
  { value: 'DISPENSA', label: 'Dispensa' },
  { value: 'CONCORRENCIA', label: 'Concorrência' },
  { value: 'OUTRA', label: 'Outra' },
] as const;

export const SPHERES = [
  { value: 'MUNICIPAL', label: 'Municipal' },
  { value: 'ESTADUAL', label: 'Estadual' },
  { value: 'FEDERAL', label: 'Federal' },
  { value: 'OUTRA', label: 'Outra' },
] as const;
