import type { DealResponseStatus, PipelineStageResponseKind } from '../../api/generated/model';

export const PRIORITIES = [
  { value: 1, label: 'Baixa' },
  { value: 2, label: 'Média' },
  { value: 3, label: 'Alta' },
] as const;

export const DEAL_STATUSES: { value: DealResponseStatus; label: string; tone: string }[] = [
  { value: 'EM_ANDAMENTO', label: 'Em andamento', tone: 'blue' },
  { value: 'GANHA', label: 'Ganha', tone: 'green' },
  { value: 'PERDIDA', label: 'Perdida', tone: 'red' },
];

export function statusLabel(status: DealResponseStatus): string {
  return DEAL_STATUSES.find((s) => s.value === status)?.label ?? status;
}

/** Etapas abertas vão do cinza ao azul conforme avançam; ganha é verde e perdida, vermelha. */
const OPEN_COLORS = ['#94a3b8', '#60a5fa', '#3b82f6', '#6366f1', '#8b5cf6', '#a855f7'];

export function stageColor(kind: PipelineStageResponseKind, openIndex: number): string {
  if (kind === 'GANHA') return '#16a34a';
  if (kind === 'PERDIDA') return '#dc2626';
  return OPEN_COLORS[Math.min(openIndex, OPEN_COLORS.length - 1)];
}
