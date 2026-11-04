import { CurrencyPipe, DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { ContractsService } from '../../api/generated/contracts/contracts.service';
import { DealsService } from '../../api/generated/deals/deals.service';
import { PriceRecordsService } from '../../api/generated/price-records/price-records.service';
import { TendersService } from '../../api/generated/tenders/tenders.service';
import type {
  ContractEventResponse,
  NextStatusResponse,
  PriceRecordEventResponse,
  TenderContactResponse,
  TenderEventResponse,
  TenderResponse,
} from '../../api/generated/model';
import { ApiError } from '../../core/http/api-error';
import { daysSince } from '../../shared/format';
import { Avatar } from '../../shared/ui/avatar';
import { Icon } from '../../shared/ui/icon';
import { AtaSection } from '../price-records/ata-section';
import { CaronaPreset } from '../price-records/carona-dialog';
import { DocumentsSection } from '../price-records/documents-section';
import { ContactDialog, OrderFromText } from './contact-dialog';
import { ContractsTab } from './contracts/contracts-tab';
import { TenderDealsTab, TenderTasksTab } from './tender-work-tabs';
import { TenderForm } from './tender-form';
import { ACTION_LABELS, statusTone } from './tender-meta';
import { TransitionDialog } from './transition-dialog';

type HistoryItem =
  | { source: 'tender'; at: string; tender: TenderEventResponse }
  | { source: 'contract'; at: string; contract: ContractEventResponse }
  | { source: 'record'; at: string; record: PriceRecordEventResponse }
  | { source: 'contact'; at: string; contact: TenderContactResponse };

type DetailTab = 'resumo' | 'itens' | 'documentos' | 'historico' | 'tarefas' | 'negociacao';
const TABS: DetailTab[] = ['resumo', 'itens', 'documentos', 'historico', 'tarefas', 'negociacao'];

@Component({
  selector: 'app-tender-detail-page',
  imports: [
    CurrencyPipe,
    DatePipe,
    RouterLink,
    Avatar,
    Icon,
    TenderForm,
    TransitionDialog,
    ContractsTab,
    TenderTasksTab,
    TenderDealsTab,
    AtaSection,
    DocumentsSection,
    ContactDialog,
  ],
  templateUrl: './tender-detail.page.html',
  styleUrl: './tender-detail.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TenderDetailPage {
  private readonly api = inject(TendersService);
  private readonly contractsApi = inject(ContractsService);
  private readonly recordsApi = inject(PriceRecordsService);
  private readonly dealsApi = inject(DealsService);
  private readonly route = inject(ActivatedRoute);

  /** Vem da rota (:id) por withComponentInputBinding. */
  readonly id = input.required<string>();

  protected readonly tone = statusTone;
  protected readonly actionLabels = ACTION_LABELS;
  /** A aba pode vir na URL (?aba=documentos), por exemplo do "Registrar carona" do funil. */
  protected readonly tab = signal<DetailTab>(
    initialTab(this.route.snapshot.queryParamMap.get('aba')),
  );
  protected readonly editing = signal(false);
  protected readonly transitionTarget = signal<NextStatusResponse | null>(null);
  /**
   * Pedido do cabeçalho para a aba de contratos abrir "Registrar contrato". Zera quando a pessoa sai da aba: ao
   * voltar, a aba é criada de novo e não deve reabrir o diálogo sozinha.
   */
  protected readonly signRequest = signal(0);
  private readonly resetSignRequest = effect(() => {
    if (this.tab() !== 'itens') this.signRequest.set(0);
  });

  protected readonly tender = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.getTender(params),
  });
  protected readonly history = rxResource({
    params: () => (this.tab() === 'historico' ? this.id() : undefined),
    stream: ({ params }) => this.api.tenderHistory(params),
  });
  protected readonly recordHistory = rxResource({
    params: () => (this.tab() === 'historico' ? this.id() : undefined),
    stream: ({ params }) => this.recordsApi.priceRecordHistory({ tenderId: params }),
  });
  /** Ata vigente que permite adesão e ainda tem vaga: destaque no cabeçalho. */
  protected readonly highlight = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.recordsApi.priceRecordHighlights({ tenderIds: [params] }),
  });
  protected readonly ataHighlight = computed(
    () => this.highlight.value()?.find((h) => h.opportunity) ?? null,
  );
  /** ?carona=<negociação>: a carona chega com a negociação e o órgão dela. */
  private readonly caronaDeal = rxResource({
    params: () => this.route.snapshot.queryParamMap.get('carona') ?? undefined,
    stream: ({ params }) =>
      this.dealsApi
        .getDeal(params)
        .pipe(map((d): CaronaPreset => ({ dealId: d.id, agency: d.agency ?? undefined }))),
  });
  protected readonly caronaPreset = computed(() => this.caronaDeal.value() ?? null);
  /** ?kit=<negociação> (Fase 10): o kit de carona abre com o município da negociação. */
  private readonly kitDeal = rxResource({
    params: () => this.route.snapshot.queryParamMap.get('kit') ?? undefined,
    stream: ({ params }) => this.dealsApi.getDeal(params).pipe(map((d) => d.agency ?? null)),
  });
  protected readonly kitAgency = computed(() => this.kitDeal.value() ?? null);
  protected readonly contractHistory = rxResource({
    params: () => (this.tab() === 'historico' ? this.id() : undefined),
    stream: ({ params }) => this.contractsApi.tenderContractsHistory(params),
  });
  /** Contatos com o órgão (Fase 9), na mesma linha do tempo. */
  protected readonly contacts = rxResource({
    params: () => (this.tab() === 'historico' ? this.id() : undefined),
    stream: ({ params }) => this.api.tenderContacts(params),
  });
  protected readonly contactOpen = signal(false);
  protected readonly confirmingContact = signal<string | null>(null);
  protected readonly contactError = signal<string | null>(null);
  /** Resposta do órgão que já é o pedido: a aba de contratos abre "Registrar pedido" com o texto. */
  protected readonly orderRequest = signal<{
    contractId: string | null;
    text: string;
    seq: number;
  } | null>(null);

  /** Histórico da licitação e dos contratos numa linha do tempo só, do mais recente para o mais antigo. */
  protected readonly historyItems = computed<HistoryItem[]>(() => {
    const tenderEvents: HistoryItem[] = (this.history.value() ?? []).map((e) => ({
      source: 'tender',
      at: e.occurredAt,
      tender: e,
    }));
    const contractEvents: HistoryItem[] = (this.contractHistory.value() ?? []).map((e) => ({
      source: 'contract',
      at: e.occurredAt,
      contract: e,
    }));
    const recordEvents: HistoryItem[] = (this.recordHistory.value() ?? []).map((e) => ({
      source: 'record',
      at: e.occurredAt,
      record: e,
    }));
    const contacts: HistoryItem[] = (this.contacts.value() ?? []).map((c) => ({
      source: 'contact',
      at: c.createdAt,
      contact: c,
    }));
    return [...tenderEvents, ...contractEvents, ...recordEvents, ...contacts].sort((a, b) =>
      b.at.localeCompare(a.at),
    );
  });

  /** Vencida ou aguardando assinatura: a próxima ação é registrar o contrato. */
  protected readonly awaitsContract = computed(() => {
    const s = this.tender.value()?.status;
    return s === 'VENCIDA' || s === 'AGUARDANDO_ASSINATURA';
  });

  protected readonly notFound = computed(() => {
    const err = this.tender.error();
    return err instanceof ApiError && err.code === 'NOT_FOUND';
  });

  protected readonly tabs: { id: DetailTab; label: string }[] = [
    { id: 'resumo', label: 'Resumo' },
    { id: 'itens', label: 'Itens e pedidos' },
    { id: 'documentos', label: 'Documentos' },
    { id: 'historico', label: 'Histórico' },
    { id: 'tarefas', label: 'Tarefas' },
    { id: 'negociacao', label: 'Negociação' },
  ];

  /** Linha do tempo do ciclo de vida: o que já aconteceu fica marcado. */
  protected readonly timeline = computed(() => {
    const t = this.tender.value();
    if (!t) return [];
    return [
      { label: 'Vencida', date: t.wonAt },
      { label: 'Contrato assinado', date: t.signedAt },
      { label: 'Primeiro pedido', date: t.ordersStartedAt },
      { label: 'Encerrado', date: t.closedAt },
    ];
  });

  protected startContract(): void {
    this.tab.set('itens');
    this.signRequest.update((n) => n + 1);
  }

  /** Contratos mudaram: a licitação pode ter mudado de situação, datas e versão. */
  protected onContractsChanged(): void {
    this.tender.reload();
    this.history.reload();
    this.contractHistory.reload();
  }

  protected onContactSaved(): void {
    this.contactOpen.set(false);
    this.contacts.reload();
  }

  protected removeContact(contactId: string): void {
    this.contactError.set(null);
    this.api.removeTenderContact(this.id(), contactId).subscribe({
      next: () => {
        this.confirmingContact.set(null);
        this.contacts.reload();
      },
      error: (err: unknown) => {
        this.confirmingContact.set(null);
        this.contactError.set(err instanceof ApiError ? err.detail : 'Não foi possível apagar.');
      },
    });
  }

  /** A resposta do órgão já é o pedido: vai para "Itens e pedidos" e abre o pedido com o texto (Fase 8). */
  protected orderFromContact(request: OrderFromText): void {
    this.contactOpen.set(false);
    this.tab.set('itens');
    this.orderRequest.set({ ...request, seq: (this.orderRequest()?.seq ?? 0) + 1 });
  }

  protected onAtaChanged(): void {
    this.highlight.reload();
    this.recordHistory.reload();
  }

  protected onSaved(t: TenderResponse): void {
    this.editing.set(false);
    this.transitionTarget.set(null);
    this.tender.set(t);
    this.history.reload();
  }

  protected days(iso: string | null): number {
    return daysSince(iso);
  }
}

function initialTab(value: string | null): DetailTab {
  return TABS.includes(value as DetailTab) ? (value as DetailTab) : 'resumo';
}
