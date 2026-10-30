import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { filter, map, of } from 'rxjs';
import { ContractsService } from '../../api/generated/contracts/contracts.service';
import { PriceRecordsService } from '../../api/generated/price-records/price-records.service';
import { TendersService } from '../../api/generated/tenders/tenders.service';
import { UsersService } from '../../api/generated/users/users.service';
import type {
  PriceRecordHighlightResponse,
  SearchTendersParams,
  TenderContractTotalsResponse,
  TenderResponse,
} from '../../api/generated/model';
import { Session } from '../../core/auth/session';
import { UFS, daysSince } from '../../shared/format';
import { Avatar } from '../../shared/ui/avatar';
import { Icon } from '../../shared/ui/icon';
import { Pager } from '../../shared/ui/pager';
import { TenderForm } from './tender-form';
import { DEFAULT_TAB, TENDER_TABS, statusTone } from './tender-meta';

const PAGE_SIZE = 25;

/** Abas em que a licitação já tem contrato: mostram quanto foi pedido e quanto falta. */
const CONTRACT_TABS = ['assinados', 'andamento', 'encerrados'];

/** Lista de licitações. Aba, busca, filtros, ordenação e página ficam na URL (dá para compartilhar e voltar). */
@Component({
  selector: 'app-tenders-page',
  imports: [CurrencyPipe, DatePipe, RouterLink, Icon, Avatar, Pager, TenderForm],
  templateUrl: './tenders.page.html',
  styleUrl: './tenders.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TendersPage {
  private readonly api = inject(TendersService);
  private readonly usersApi = inject(UsersService);
  private readonly contractsApi = inject(ContractsService);
  private readonly recordsApi = inject(PriceRecordsService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly session = inject(Session);

  protected readonly tabs = TENDER_TABS;
  protected readonly ufs = UFS;
  protected readonly tone = statusTone;
  protected readonly formOpen = signal(false);
  protected readonly filtersOpen = signal(false);

  private readonly params = toSignal(this.route.queryParamMap.pipe(map((p) => p)), {
    initialValue: this.route.snapshot.queryParamMap,
  });

  protected readonly tabId = computed(() => this.params().get('aba') ?? DEFAULT_TAB);
  protected readonly tab = computed(
    () => this.tabs.find((t) => t.id === this.tabId()) ?? this.tabs[1],
  );
  protected readonly q = computed(() => this.params().get('q') ?? '');
  protected readonly ownerId = computed(() => this.params().get('responsavel') ?? '');
  protected readonly materialType = computed(() => this.params().get('material') ?? '');
  protected readonly state = computed(() => this.params().get('uf') ?? '');
  protected readonly page = computed(() => Number(this.params().get('pagina') ?? 0));
  protected readonly sort = computed(() => this.params().get('ordem') ?? 'value');
  protected readonly direction = computed(() => this.params().get('direcao') ?? 'desc');
  protected readonly activeFilters = computed(
    () => [this.ownerId(), this.materialType(), this.state()].filter((v) => !!v).length,
  );

  private readonly filter = computed(() => ({
    q: this.q() || undefined,
    ownerId: this.ownerId() || undefined,
    materialType: this.materialType() || undefined,
    state: this.state() || undefined,
  }));

  protected readonly summary = rxResource({
    params: this.filter,
    stream: ({ params }) => this.api.tenderSummary(params),
  });

  protected readonly list = rxResource({
    params: computed((): SearchTendersParams => ({
      ...this.filter(),
      status: this.tab().statuses,
      page: this.page(),
      size: PAGE_SIZE,
      sort: this.sort(),
      direction: this.direction(),
    })),
    stream: ({ params }) => this.api.searchTenders(params),
  });

  /** Pedido e saldo dos contratos das licitações da página, numa consulta só. */
  protected readonly contractTotals = rxResource({
    params: computed(() => {
      if (!CONTRACT_TABS.includes(this.tabId())) return undefined;
      const ids = (this.list.value()?.items ?? []).map((t) => t.id);
      return ids.length ? ids : undefined;
    }),
    stream: ({ params }) =>
      params ? this.contractsApi.contractTotalsByTender({ tenderIds: params }) : of([]),
  });
  protected readonly totalsByTender = computed(() => {
    const byId = new Map<string, TenderContractTotalsResponse>();
    for (const t of this.contractTotals.value() ?? []) byId.set(t.tenderId, t);
    return byId;
  });

  /** Encerradas com ata ainda vigente: "a ata é dinheiro" (seção 3.3 da especificação). */
  protected readonly atas = rxResource({
    params: computed(() => {
      if (this.tabId() !== 'encerrados') return undefined;
      const ids = (this.list.value()?.items ?? []).map((t) => t.id);
      return ids.length ? ids : undefined;
    }),
    stream: ({ params }) =>
      params ? this.recordsApi.priceRecordHighlights({ tenderIds: params }) : of([]),
  });
  protected readonly atasByTender = computed(() => {
    const byId = new Map<string, PriceRecordHighlightResponse>();
    for (const h of this.atas.value() ?? []) byId.set(h.tenderId, h);
    return byId;
  });

  protected readonly owners = rxResource({ stream: () => this.usersApi.listUserOptions() });
  protected readonly suggestions = rxResource({ stream: () => this.api.tenderSuggestions() });

  protected readonly tabTotals = computed(() => {
    const rows = this.summary.value() ?? [];
    return this.tabs.map((t) => {
      const parts = rows.filter((r) => t.statuses.includes(r.status));
      return {
        tab: t,
        count: parts.reduce((a, r) => a + r.count, 0),
        value: parts.reduce((a, r) => a + r.value, 0),
      };
    });
  });
  protected readonly currentTotal = computed(() =>
    this.tabTotals().find((t) => t.tab.id === this.tabId()),
  );
  protected readonly canCreate = computed(() => this.session.user()?.role !== 'VIEWER');

  constructor() {
    // "Nova licitação" da barra superior manda ?novo=1; ouvir a query (e não só a primeira) abre o formulário
    // também quando a lista já está aberta e o roteador reaproveita a página
    this.route.queryParamMap
      .pipe(
        filter((p) => p.get('novo') === '1'),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        this.formOpen.set(true);
        this.navigate({ novo: null }, false);
      });
  }

  protected navigate(changes: Record<string, string | number | null>, resetPage = true): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: resetPage ? { ...changes, pagina: changes['pagina'] ?? null } : changes,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  private searchTimer?: ReturnType<typeof setTimeout>;
  protected onSearch(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.navigate({ q: value.trim() || null }), 300);
  }

  protected setFilter(name: string, event: Event): void {
    this.navigate({ [name]: (event.target as HTMLSelectElement).value || null });
  }

  protected clearFilters(): void {
    this.navigate({ responsavel: null, material: null, uf: null });
  }

  protected sortBy(column: string): void {
    const same = this.sort() === column;
    this.navigate({ ordem: column, direcao: same && this.direction() === 'desc' ? 'asc' : 'desc' });
  }

  protected ariaSort(column: string): 'ascending' | 'descending' | null {
    if (this.sort() !== column) return null;
    return this.direction() === 'asc' ? 'ascending' : 'descending';
  }

  protected open(t: TenderResponse): void {
    void this.router.navigate(['/licitacoes', t.id]);
  }

  protected onSaved(t: TenderResponse): void {
    this.formOpen.set(false);
    void this.router.navigate(['/licitacoes', t.id]);
  }

  protected days(iso: string | null): number {
    return daysSince(iso);
  }

  protected readonly skeletonRows = Array.from({ length: 6 });
}
