/** Palavras comuns em nome de órgão, que não ajudam a dizer se dois nomes são o mesmo órgão. */
const GENERIC = new Set([
  'prefeitura',
  'municipal',
  'municipio',
  'camara',
  'secretaria',
  'fundo',
  'estado',
  'estadual',
  'governo',
  'fundacao',
  'instituto',
  'servico',
  'servicos',
  'autarquia',
  'publico',
  'publica',
  'saude',
  'educacao',
  'assistencia',
  'social',
]);

function significant(name: string): Set<string> {
  const plain = name.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
  return new Set(plain.split(/[^a-z0-9]+/).filter((w) => w.length >= 4 && !GENERIC.has(w)));
}

/**
 * Se dois nomes parecem ser do mesmo órgão: alguma palavra significativa (4 letras ou mais, fora "prefeitura",
 * "municipal"...) aparece nos dois. Sem palavra significativa, não dá para dizer, e conta como o mesmo. É a mesma
 * regra da conferência do pedido no backend (OrderReadingResult.sameAgency).
 */
export function sameAgency(read: string, expected: string): boolean {
  const a = significant(read);
  const b = significant(expected);
  if (!a.size || !b.size) return true;
  return [...b].some((w) => a.has(w));
}
