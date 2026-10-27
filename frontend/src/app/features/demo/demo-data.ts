/**
 * DADOS DE EXEMPLO para a prévia visual, tirados dos exemplos da especificação (seção 3) e da planilha de
 * controle do cliente. UASG, pregões, datas e responsáveis são fictícios. Somem quando a API da Fase 1 entrar;
 * nada aqui é regra de negócio. Os totais sempre são calculados a partir das linhas.
 */

export type TenderStatus = 'VENCIDA' | 'ASSINADO' | 'EM_ANDAMENTO' | 'ENCERRADO';

export interface DemoTender {
  id: string;
  agency: string;
  city: string;
  uasgPregao: string;
  material: string;
  owner: string;
  value: number;
  ordered: number;
  status: TenderStatus;
  wonAt: string;
  signedAt?: string;
  closedAt?: string;
  ataUntil?: string;
}

export const OWNERS = ['Edwin Lima', 'Carla Mendes', 'Rafael Souza'];

export const TENDERS: DemoTender[] = [
  {
    id: 't1',
    agency: 'Prefeitura Municipal de Magé',
    city: 'Magé/RJ',
    uasgPregao: 'PE 041/2026',
    material: 'Eletro e papelaria',
    owner: 'Edwin Lima',
    value: 174000,
    ordered: 0,
    status: 'VENCIDA',
    wonAt: '2026-09-18',
  },
  {
    id: 't2',
    agency: 'Brigada de Infantaria Paraquedista',
    city: 'Rio de Janeiro/RJ',
    uasgPregao: 'UASG 160221 / PE 012/2026',
    material: 'Expediente escolar',
    owner: 'Carla Mendes',
    value: 425034.07,
    ordered: 0,
    status: 'VENCIDA',
    wonAt: '2026-09-25',
  },
  {
    id: 't3',
    agency: 'MEC · INES Inst. Nac. de Educação de Surdos',
    city: 'Rio de Janeiro/RJ',
    uasgPregao: 'UASG 152005 / PE 027/2026',
    material: 'Material gráfico',
    owner: 'Edwin Lima',
    value: 425232,
    ordered: 0,
    status: 'VENCIDA',
    wonAt: '2026-10-02',
  },
  {
    id: 't4',
    agency: 'Comando da 1ª Região Militar',
    city: 'Rio de Janeiro/RJ',
    uasgPregao: 'UASG 160313 / PE 039/2026',
    material: 'Material gráfico',
    owner: 'Rafael Souza',
    value: 70900.26,
    ordered: 0,
    status: 'VENCIDA',
    wonAt: '2026-10-09',
  },
  {
    id: 't5',
    agency: 'Prefeitura Municipal de Cantagalo',
    city: 'Cantagalo/RJ',
    uasgPregao: 'PE 018/2026',
    material: 'Expediente escolar',
    owner: 'Carla Mendes',
    value: 287201.54,
    ordered: 0,
    status: 'VENCIDA',
    wonAt: '2026-10-14',
  },
  {
    id: 't6',
    agency: 'Prefeitura Municipal de Mesquita',
    city: 'Mesquita/RJ',
    uasgPregao: 'PE 055/2026',
    material: 'Lixeiras',
    owner: 'Edwin Lima',
    value: 310000,
    ordered: 0,
    status: 'VENCIDA',
    wonAt: '2026-10-20',
  },
  {
    id: 't7',
    agency: 'Prefeitura de Conceição de Macabu',
    city: 'Conceição de Macabu/RJ',
    uasgPregao: 'PE 022/2026',
    material: 'Kit material escolar',
    owner: 'Rafael Souza',
    value: 213000,
    ordered: 0,
    status: 'VENCIDA',
    wonAt: '2026-10-22',
  },
  {
    id: 't8',
    agency: 'SEGOV · Secretaria de Governo',
    city: 'Rio de Janeiro/RJ',
    uasgPregao: 'UASG 926105 / PE 031/2026',
    material: 'Material gráfico',
    owner: 'Edwin Lima',
    value: 89000,
    ordered: 0,
    status: 'ASSINADO',
    wonAt: '2026-04-10',
    signedAt: '2026-05-16',
  },
  {
    id: 't9',
    agency: 'Prefeitura de São João da Barra',
    city: 'São João da Barra/RJ',
    uasgPregao: 'PE 009/2026',
    material: 'Material gráfico',
    owner: 'Carla Mendes',
    value: 72570.3,
    ordered: 0,
    status: 'ASSINADO',
    wonAt: '2026-08-12',
    signedAt: '2026-09-02',
  },
  {
    id: 't10',
    agency: 'Colégio Pedro II',
    city: 'Rio de Janeiro/RJ',
    uasgPregao: 'UASG 153167 / PE 044/2026',
    material: 'Expediente escolar',
    owner: 'Rafael Souza',
    value: 3685423.59,
    ordered: 0,
    status: 'ASSINADO',
    wonAt: '2026-09-05',
    signedAt: '2026-10-10',
  },
  {
    id: 't11',
    agency: 'SEFAZ · Secretaria de Fazenda',
    city: 'Rio de Janeiro/RJ',
    uasgPregao: 'UASG 926201 / PE 017/2026',
    material: 'Material gráfico',
    owner: 'Edwin Lima',
    value: 237000,
    ordered: 43175.1,
    status: 'EM_ANDAMENTO',
    wonAt: '2026-02-03',
    signedAt: '2026-03-01',
    ataUntil: '2027-03-01',
  },
  {
    id: 't12',
    agency: 'SEGOV · Uniformes personalizados',
    city: 'Rio de Janeiro/RJ',
    uasgPregao: 'UASG 926105 / PE 033/2026',
    material: 'Uniformes',
    owner: 'Carla Mendes',
    value: 162000,
    ordered: 115622.4,
    status: 'EM_ANDAMENTO',
    wonAt: '2026-03-15',
    signedAt: '2026-04-02',
    ataUntil: '2027-04-02',
  },
  {
    id: 't13',
    agency: 'Comando do Exército',
    city: 'Brasília/DF',
    uasgPregao: 'UASG 160502 / PE 008/2026',
    material: 'Expediente',
    owner: 'Rafael Souza',
    value: 514721.2,
    ordered: 120000,
    status: 'EM_ANDAMENTO',
    wonAt: '2026-01-20',
    signedAt: '2026-02-11',
    ataUntil: '2027-02-11',
  },
  {
    id: 't14',
    agency: 'Intendência da Marinha',
    city: 'Rio de Janeiro/RJ',
    uasgPregao: 'UASG 765701 / PE 003/2026',
    material: 'Eletrodomésticos e expediente',
    owner: 'Edwin Lima',
    value: 4593738.69,
    ordered: 1250000,
    status: 'EM_ANDAMENTO',
    wonAt: '2026-01-08',
    signedAt: '2026-01-30',
    ataUntil: '2027-01-30',
  },
  {
    id: 't15',
    agency: 'SEIJES · Secretaria de Igualdade',
    city: 'Rio de Janeiro/RJ',
    uasgPregao: 'UASG 926330 / PE 014/2025',
    material: 'Material gráfico',
    owner: 'Carla Mendes',
    value: 19990,
    ordered: 19900,
    status: 'ENCERRADO',
    wonAt: '2025-11-04',
    signedAt: '2025-12-01',
    closedAt: '2026-09-30',
    ataUntil: '2028-12-01',
  },
  {
    id: 't16',
    agency: 'Casa Civil',
    city: 'Rio de Janeiro/RJ',
    uasgPregao: 'Dispensa 112/2026',
    material: 'Placas e letras em aço',
    owner: 'Edwin Lima',
    value: 3800,
    ordered: 3800,
    status: 'ENCERRADO',
    wonAt: '2026-06-02',
    signedAt: '2026-06-10',
    closedAt: '2026-08-15',
  },
  {
    id: 't17',
    agency: 'AGEVAP · Agência de Bacias',
    city: 'Resende/RJ',
    uasgPregao: 'Dispensa 034/2026',
    material: 'Material gráfico',
    owner: 'Rafael Souza',
    value: 25312,
    ordered: 25312,
    status: 'ENCERRADO',
    wonAt: '2026-05-05',
    signedAt: '2026-05-12',
    closedAt: '2026-07-20',
    ataUntil: '2027-05-12',
  },
];

export const STATUS_META: Record<TenderStatus, { label: string; tone: string; tab: string }> = {
  VENCIDA: { label: 'Aguardando contrato', tone: 'amber', tab: 'Licitações vencidas' },
  ASSINADO: { label: 'Contrato assinado', tone: 'blue', tab: 'Contratos assinados' },
  EM_ANDAMENTO: { label: 'Em andamento', tone: 'green', tab: 'Em andamento' },
  ENCERRADO: { label: 'Encerrado', tone: '', tab: 'Encerrados' },
};

export interface DemoStage {
  id: string;
  name: string;
  color: string;
}

export const STAGES: DemoStage[] = [
  { id: 's1', name: 'Sem contato', color: '#94a3b8' },
  { id: 's2', name: 'Contato feito', color: '#60a5fa' },
  { id: 's3', name: 'Identificação do interesse', color: '#818cf8' },
  { id: 's4', name: 'Apresentação', color: '#f59e0b' },
  { id: 's5', name: 'Proposta enviada', color: '#10b981' },
];

export interface DemoDeal {
  id: string;
  stageId: string;
  title: string;
  company: string;
  owner: string;
  value: number;
  priority: 1 | 2 | 3;
  isNew: boolean;
  overdueTask?: string;
}

export const DEALS: DemoDeal[] = [
  {
    id: 'd1',
    stageId: 's1',
    title: 'Carona na ata SEIJES · material gráfico',
    company: 'Prefeitura de Nova Iguaçu',
    owner: 'Edwin Lima',
    value: 39980,
    priority: 2,
    isNew: true,
  },
  {
    id: 'd2',
    stageId: 's1',
    title: 'Carona na ata SEFAZ · cartões e blocos',
    company: 'Prefeitura de Itaguaí',
    owner: 'Carla Mendes',
    value: 118500,
    priority: 1,
    isNew: true,
  },
  {
    id: 'd3',
    stageId: 's1',
    title: 'Expediente escolar 2027',
    company: 'Prefeitura de Petrópolis',
    owner: 'Rafael Souza',
    value: 96000,
    priority: 1,
    isNew: false,
  },
  {
    id: 'd4',
    stageId: 's2',
    title: 'Carona na ata Intendência · eletrodomésticos',
    company: 'Prefeitura de Maricá',
    owner: 'Edwin Lima',
    value: 460000,
    priority: 3,
    isNew: false,
    overdueTask: 'Ligar para o setor de compras · 22/10',
  },
  {
    id: 'd5',
    stageId: 's2',
    title: 'Kit escolar 2027',
    company: 'Prefeitura de Teresópolis',
    owner: 'Carla Mendes',
    value: 182400,
    priority: 2,
    isNew: false,
  },
  {
    id: 'd6',
    stageId: 's3',
    title: 'Carona na ata SEGOV · uniformes',
    company: 'Prefeitura de Niterói',
    owner: 'Rafael Souza',
    value: 81000,
    priority: 2,
    isNew: false,
  },
  {
    id: 'd7',
    stageId: 's3',
    title: 'Material gráfico · campanhas',
    company: 'Prefeitura de Duque de Caxias',
    owner: 'Edwin Lima',
    value: 145000,
    priority: 3,
    isNew: false,
    overdueTask: 'Enviar catálogo · 24/10',
  },
  {
    id: 'd8',
    stageId: 's4',
    title: 'Carona na ata Comando do Exército',
    company: 'Prefeitura de Resende',
    owner: 'Carla Mendes',
    value: 257360,
    priority: 2,
    isNew: false,
  },
  {
    id: 'd9',
    stageId: 's5',
    title: 'Carona na ata SEIJES · folders',
    company: 'Prefeitura de Volta Redonda',
    owner: 'Rafael Souza',
    value: 19990,
    priority: 1,
    isNew: false,
  },
  {
    id: 'd10',
    stageId: 's5',
    title: 'Placas de identificação',
    company: 'Câmara Municipal de Macaé',
    owner: 'Edwin Lima',
    value: 12600,
    priority: 1,
    isNew: false,
  },
];

export interface DemoTask {
  id: string;
  title: string;
  dueAt: string;
  owner: string;
  deal: string;
  company: string;
  value: number;
  done: boolean;
}

const VENCIDAS_DEAL = 'Licitações vencidas e que faltam receber o contrato para assinar';

export const TASKS: DemoTask[] = [
  {
    id: 'k1',
    title: 'Cobrar contrato · Prefeitura Municipal de Magé',
    dueAt: '2026-10-21T10:00',
    owner: 'Edwin Lima',
    deal: VENCIDAS_DEAL,
    company: 'Prefeitura Municipal de Magé',
    value: 174000,
    done: false,
  },
  {
    id: 'k2',
    title: 'Cobrar contrato · Brigada Paraquedista',
    dueAt: '2026-10-23T14:30',
    owner: 'Carla Mendes',
    deal: VENCIDAS_DEAL,
    company: 'Brigada de Infantaria Paraquedista',
    value: 425034.07,
    done: false,
  },
  {
    id: 'k3',
    title: 'Follow-up de pedido · SEGOV material gráfico',
    dueAt: '2026-10-24T09:00',
    owner: 'Edwin Lima',
    deal: 'Contratos assinados',
    company: 'SEGOV · Secretaria de Governo',
    value: 89000,
    done: false,
  },
  {
    id: 'k4',
    title: 'Cobrar contrato · MEC-INES',
    dueAt: '2026-10-27T16:00',
    owner: 'Edwin Lima',
    deal: VENCIDAS_DEAL,
    company: 'MEC · INES Inst. Nac. de Educação de Surdos',
    value: 425232,
    done: false,
  },
  {
    id: 'k5',
    title: 'Cobrar contrato · Comando da 1ª Região Militar',
    dueAt: '2026-10-28T11:00',
    owner: 'Rafael Souza',
    deal: VENCIDAS_DEAL,
    company: 'Comando da 1ª Região Militar',
    value: 70900.26,
    done: false,
  },
  {
    id: 'k6',
    title: 'Cobrar contrato · Prefeitura de Cantagalo',
    dueAt: '2026-10-29T10:30',
    owner: 'Carla Mendes',
    deal: VENCIDAS_DEAL,
    company: 'Prefeitura Municipal de Cantagalo',
    value: 287201.54,
    done: false,
  },
  {
    id: 'k7',
    title: 'Cobrar contrato · Prefeitura de Mesquita',
    dueAt: '2026-10-30T15:00',
    owner: 'Edwin Lima',
    deal: VENCIDAS_DEAL,
    company: 'Prefeitura Municipal de Mesquita',
    value: 310000,
    done: false,
  },
  {
    id: 'k8',
    title: 'Cobrar contrato · Conceição de Macabu',
    dueAt: '2026-11-03T09:30',
    owner: 'Rafael Souza',
    deal: VENCIDAS_DEAL,
    company: 'Prefeitura de Conceição de Macabu',
    value: 213000,
    done: false,
  },
];

/** "Hoje" da prévia: o relógio real, para que atrasos e prazos façam sentido. */
export const today = () => new Date();

export function daysBetween(fromIso: string, to: Date = today()): number {
  return Math.floor((to.getTime() - new Date(fromIso).getTime()) / 86_400_000);
}

export const sum = (values: number[]) => Math.round(values.reduce((a, b) => a + b, 0) * 100) / 100;
