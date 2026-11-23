const MONTHS = [
  'Janeiro',
  'Fevereiro',
  'Março',
  'Abril',
  'Maio',
  'Junho',
  'Julho',
  'Agosto',
  'Setembro',
  'Outubro',
  'Novembro',
  'Dezembro',
];

export function monthName(month: number): string {
  return MONTHS[month - 1] ?? '';
}

export function monthShort(month: number): string {
  return monthName(month).slice(0, 3);
}

const BRL = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

export function brl(value: number | null | undefined): string {
  return value == null ? '—' : BRL.format(value);
}

/** Forma curta para eixos e rótulos: R$ 950, R$ 12 mil, R$ 1,2 mi. */
export function compact(value: number, symbol = true): string {
  const abs = Math.abs(value);
  let text: string;
  if (abs >= 1_000_000) {
    text = `${(value / 1_000_000).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} mi`;
  } else if (abs >= 1_000) {
    text = `${Math.round(value / 1_000).toLocaleString('pt-BR')} mil`;
  } else {
    text = Math.round(value).toLocaleString('pt-BR');
  }
  return symbol ? `R$ ${text}` : text;
}

/** +12,5%, -3%, ou travessão sem base. */
export function signedPercent(value: number | null | undefined): string {
  if (value == null) return '—';
  const text = `${value.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%`;
  return value > 0 ? `+${text}` : text;
}

export function percent(value: number | null | undefined): string {
  return value == null ? '—' : `${value.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%`;
}

/**
 * Topo do eixo com 4 divisões redondas: primeiro escolhe o passo (1, 2, 2,5 ou 5 × 10ⁿ) e o topo é 4 passos. Assim
 * as marcas saem R$ 0 / 500 mil / 1 mi / 1,5 mi / 2 mi, nunca 1,3 mi. Igual ao PDF.
 */
export function niceMax(max: number): number {
  if (max <= 0) return 4;
  const raw = max / 4;
  const power = 10 ** Math.floor(Math.log10(raw));
  for (const step of [1, 2, 2.5, 5, 10]) {
    if (step * power * 4 >= max) return step * power * 4;
  }
  return 40 * power;
}

/** Rótulo do mês no eixo: "Jan" com espaço, só a inicial quando cada mês tem menos de 30 px (celular). */
export function axisMonth(month: number, slotWidth: number): string {
  return slotWidth < 30 ? monthName(month).charAt(0) : monthShort(month);
}
