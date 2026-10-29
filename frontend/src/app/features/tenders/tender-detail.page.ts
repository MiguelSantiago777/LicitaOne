import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { ContractsService } from '../../api/generated/contracts/contracts.service';
import { TendersService } from '../../api/generated/tenders/tenders.service';
import type {
  ContractEventResponse,
  NextStatusResponse,
  TenderEventResponse,
  TenderResponse,
} from '../../api/generated/model';
import { ApiError } from '../../core/http/api-error';
import { daysSince } from '../../shared/format';
import { Avatar } from '../../shared/ui/avatar';
import { Icon } from '../../shared/ui/icon';
import { ContractsTab } from './contracts/contracts-tab';
import { TenderDealsTab, TenderTasksTab } from './tender-work-tabs';
import { TenderForm } from './tender-form';
import { ACTION_LABELS, statusTone } from './tender-meta';
import { TransitionDialog } from './transition-dialog';

type HistoryItem =
  | { source: 'tender'; at: string; tender: TenderEventResponse }
  | { source: 'contract'; at: string; contract: ContractEventResponse };

type DetailTab = 'resumo' | 'itens' | 'documentos' | 'historico' | 'tarefas' | 'negociacao';

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
  ],
  templateUrl: './tender-detail.page.html',
  styleUrl: './tender-detail.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TenderDetailPage {
  private readonly api = inject(TendersService);
  private readonly contractsApi = inject(ContractsService);

  /** Vem da rota (:id) por withComponentInputBinding. */
  readonly id = input.required<string>();

  protected readonly tone = statusTone;
  protected readonly actionLabels = ACTION_LABELS;
  protected readonly tab = signal<DetailTab>('resumo');
  protected readonly editing = signal(false);
  protected readonly transitionTarget = signal<NextStatusResponse | null>(null);
  /** Pedido do cabeçalho para a aba de contratos abrir "Registrar contrato". */
  protected readonly signRequest = signal(0);

  protected readonly tender = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.getTender(params),
  });
  protected readonly history = rxResource({
    params: () => (this.tab() === 'historico' ? this.id() : undefined),
    stream: ({ params }) => this.api.tenderHistory(params),
  });
  protected readonly contractHistory = rxResource({
    params: () => (this.tab() === 'historico' ? this.id() : undefined),
    stream: ({ params }) => this.contractsApi.tenderContractsHistory(params),
  });

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
    return [...tenderEvents, ...contractEvents].sort((a, b) => b.at.localeCompare(a.at));
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
