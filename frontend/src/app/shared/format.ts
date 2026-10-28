/** Converte o texto digitado ("174.000,00", "R$ 1.234,5", "1234.56") em número. Devolve null se não der. */
export function parseBrl(text: string | null | undefined): number | null {
  if (text == null) return null;
  const clean = String(text).replace(/[R$\s]/g, '');
  if (!clean) return null;
  const normalized = clean.includes(',') ? clean.replace(/\./g, '').replace(',', '.') : clean;
  if (!/^\d+(\.\d{1,2})?$/.test(normalized)) return null;
  return Math.round(Number(normalized) * 100) / 100;
}

/** Quantidade digitada ("1.200", "2,5", "1200") → número com até 3 casas. Devolve null se não der. */
export function parseQuantity(text: string | null | undefined): number | null {
  if (text == null) return null;
  const clean = String(text).replace(/\s/g, '');
  if (!clean) return null;
  const normalized = clean.includes(',')
    ? clean.replace(/\./g, '').replace(',', '.')
    : /^\d{1,3}(\.\d{3})+$/.test(clean)
      ? clean.replace(/\./g, '')
      : clean;
  if (!/^\d+(\.\d{1,3})?$/.test(normalized)) return null;
  return Number(normalized);
}

const qty = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 3 });

/** 1200 → "1.200"; 2.5 → "2,5". */
export function formatQuantity(value: number | null | undefined): string {
  return value == null ? '' : qty.format(value);
}

const brl = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** 174000 → "174.000,00" (para o campo de valor; o "R$" fica fora do campo). */
export function formatBrlInput(value: number | null | undefined): string {
  return value == null ? '' : brl.format(value);
}

/** ISO com fuso → valor de <input type="datetime-local"> no horário local. */
export function toLocalDateTimeInput(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** Valor de <input type="datetime-local"> → ISO com fuso. */
export function fromLocalDateTimeInput(value: string | null | undefined): string | undefined {
  return value ? new Date(value).toISOString() : undefined;
}

/** Data de hoje no formato do <input type="date">. */
export function todayIso(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Dias entre uma data ISO e hoje (positivo = no passado). */
export function daysSince(iso: string | null | undefined): number {
  if (!iso) return 0;
  const start = new Date(iso.length === 10 ? `${iso}T00:00:00` : iso);
  return Math.floor((Date.now() - start.getTime()) / 86_400_000);
}

export const UFS = [
  'AC',
  'AL',
  'AP',
  'AM',
  'BA',
  'CE',
  'DF',
  'ES',
  'GO',
  'MA',
  'MT',
  'MS',
  'MG',
  'PA',
  'PB',
  'PR',
  'PE',
  'PI',
  'RJ',
  'RN',
  'RS',
  'RO',
  'RR',
  'SC',
  'SP',
  'SE',
  'TO',
];
