import type { PriceRecordResponseSituation } from '../../api/generated/model';

/** Etiqueta da vigência: "Vence em 42 dias" já diz mais que "Vencendo". */
export function situationLabel(situation: PriceRecordResponseSituation, daysLeft: number): string {
  if (situation === 'VENCIDA') return `Vencida há ${-daysLeft} ${-daysLeft === 1 ? 'dia' : 'dias'}`;
  if (situation === 'VENCENDO')
    return daysLeft === 0
      ? 'Vence hoje'
      : `Vence em ${daysLeft} ${daysLeft === 1 ? 'dia' : 'dias'}`;
  return 'Vigente';
}

export function situationTone(situation: PriceRecordResponseSituation): string {
  if (situation === 'VENCIDA') return 'red';
  if (situation === 'VENCENDO') return 'amber';
  return 'green';
}

/** Quantidade em pt-BR com até 3 casas ("1.200", "2,5"). */
const qty = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 3 });
export function formatQty(value: number | null | undefined): string {
  return value == null ? '' : qty.format(value);
}
