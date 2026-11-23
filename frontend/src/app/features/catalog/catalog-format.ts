/** Cotação mais velha que isso aparece como antiga (decisão de 23/11/2026; o servidor usa a mesma regra). */
export const STALE_AFTER_DAYS = 90;

/** Data ISO (aaaa-mm-dd) em dd/mm/aaaa, ou travessão. */
export function formatDay(iso: string | null | undefined): string {
  if (!iso) return '—';
  const [y, m, d] = iso.split('-');
  return `${d}/${m}/${y}`;
}

/** Inteiro digitado (prazo em dias), ou null se em branco ou inválido. */
export function parseDays(text: string | null | undefined): number | null {
  const clean = (text ?? '').trim();
  return /^\d{1,4}$/.test(clean) ? Number(clean) : null;
}

/** Tamanho de arquivo legível: 120 KB, 2,4 MB. */
export function formatSize(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`;
}
