export type ProposalKind = 'PRODUTO' | 'SERVICO';

export const KINDS: { id: ProposalKind; label: string; short: string }[] = [
  { id: 'PRODUTO', label: 'Fornecimento de produtos', short: 'Produto' },
  { id: 'SERVICO', label: 'Prestação de serviços', short: 'Serviço' },
];

/** "11222333000181" → "11.222.333/0001-81". Aceita o CNPJ alfanumérico. Outro tamanho volta como veio. */
export function formatCnpj(value: string | null | undefined): string {
  if (!value) return '';
  const plain = value.replace(/[\s./-]/g, '').toUpperCase();
  if (plain.length !== 14) return value;
  return `${plain.slice(0, 2)}.${plain.slice(2, 5)}.${plain.slice(5, 8)}/${plain.slice(8, 12)}-${plain.slice(12)}`;
}

/** "52998224725" → "529.982.247-25". Outro tamanho volta como veio. */
export function formatCpf(value: string | null | undefined): string {
  if (!value) return '';
  const digits = value.replace(/\D/g, '');
  if (digits.length !== 11) return value;
  return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6, 9)}-${digits.slice(9)}`;
}

/**
 * Valor unitário digitado ("25,90", "R$ 1.234,5678", "189.5") → número com até 4 casas. Devolve null se não der.
 */
export function parseUnitPrice(text: string | null | undefined): number | null {
  if (text == null) return null;
  const clean = String(text).replace(/[R$\s]/g, '');
  if (!clean) return null;
  const normalized = clean.includes(',') ? clean.replace(/\./g, '').replace(',', '.') : clean;
  if (!/^\d+(\.\d{1,4})?$/.test(normalized)) return null;
  return Number(normalized);
}

const price = new Intl.NumberFormat('pt-BR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 4,
});

/** 25.9 → "25,90"; 42.3333 → "42,3333" (para o campo; o "R$" fica fora). */
export function formatUnitPrice(value: number | null | undefined): string {
  return value == null ? '' : price.format(value);
}

/** Total da linha em centavos, como o servidor (meio para cima). */
export function lineTotal(quantity: number | null, unitPrice: number | null): number | null {
  if (quantity == null || unitPrice == null) return null;
  return Math.round(quantity * unitPrice * 100 + Number.EPSILON) / 100;
}
