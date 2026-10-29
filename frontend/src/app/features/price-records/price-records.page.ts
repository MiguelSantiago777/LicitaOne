import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, map } from 'rxjs';
import { DealsService } from '../../api/generated/deals/deals.service';
import type {
  AgencyRefResponse,
  DealTenderResponse,
  ListPriceRecordsFilter,
  PriceRecordSummaryResponse,
} from '../../api/generated/model';
import { PriceRecordsService } from '../../api/generated/price-records/price-records.service';
import { Session } from '../../core/auth/session';
import { UFS } from '../../shared/format';
import { Avatar } from '../../shared/ui/avatar';
import { Icon } from '../../shared/ui/icon';
import { DealDialog } from '../deals/deal-dialog';
import { situationLabel, situationTone } from './price-record-meta';

type Tab = ListPriceRecordsFilter | 'MUNICIPIOS';

const TABS: { id: Tab; label: string }[] = [
  { id: 'OPORTUNIDADES', label: 'Oportunidades' },
  { id: 'VIGENTES', label: 'Vigentes' },
  { id: 'VENCENDO', label: 'Vencendo' },
  { id: 'VENCIDAS', label: 'Vencidas' },
  { id: 'TODAS', label: 'Todas' },
  { id: 'MUNICIPIOS', label: 'Municípios sem ata' },
];

/**
 * Atas vigentes e oportunidades de carona, pelo vencimento, e os municípios onde a Shema ainda não tem ata (para
 * prospectar). "Oferecer carona" e "Nova negociação" abrem o funil já preenchido. Aba, busca e UF ficam na URL.
 */
@Component({
  selector: 'app-price-records-page',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, FormsModule, RouterLink, Avatar, Icon, DealDialog],
  templateUrl: './price-records.page.html',
  styleUrl: './price-records.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PriceRecordsPage {
  private readonly api = inject(PriceRecordsService);
  private readonly dealsApi = inject(DealsService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly session = inject(Session);

  protected readonly tabs = TABS;
  protected readonly ufs = UFS;
  protected readonly situationLabel = situationLabel;
  protected readonly tone = situationTone;

  private readonly params = toSignal(this.route.queryParamMap.pipe(map((p) => p)), {
    initialValue: this.route.snapshot.queryParamMap,
  });
  protected readonly tab = computed(() => {
    const value = this.params().get('aba') as Tab | null;
    return TABS.some((t) => t.id === value) ? value! : 'OPORTUNIDADES';
  });
  protected readonly q = computed(() => this.params().get('q') ?? '');
  protected readonly state = computed(() => this.params().get('uf') ?? 'RJ');

  protected readonly records = rxResource({
    params: computed(() =>
      this.tab() === 'MUNICIPIOS'
        ? undefined
        : { filter: this.tab() as ListPriceRecordsFilter, q: this.q() || undefined },
    ),
    stream: ({ params }) => this.api.listPriceRecords(params),
  });
  protected readonly prospects = rxResource({
    params: computed(() =>
      this.tab() === 'MUNICIPIOS'
        ? { state: this.state() || undefined, q: this.q() || undefined }
        : undefined,
    ),
    stream: ({ params }) => this.api.listProspects(params),
  });
  /** Contadores das abas de atas, sem filtro de busca. */
  protected readonly all = rxResource({
    stream: () => this.api.listPriceRecords({ filter: 'TODAS' }),
  });
  protected readonly counts = computed(() => {
    const all = this.all.value() ?? [];
    return {
      OPORTUNIDADES: all.filter((r) => r.opportunity).length,
      VIGENTES: all.filter((r) => r.situation !== 'VENCIDA').length,
      VENCENDO: all.filter((r) => r.situation === 'VENCENDO').length,
      VENCIDAS: all.filter((r) => r.situation === 'VENCIDA').length,
      TODAS: all.length,
    } as Record<string, number>;
  });

  protected readonly canCreateDeal = computed(() => this.session.user()?.role !== 'VIEWER');
  protected readonly dealOpen = signal(false);
  protected readonly presetTender = signal<DealTenderResponse | null>(null);
  protected readonly presetAgency = signal<AgencyRefResponse | null>(null);
  protected readonly created = signal<string | null>(null);
  private readonly pipeline = rxResource({
    params: () => (this.dealOpen() ? true : undefined),
    stream: () => this.dealsApi.getPipeline({}),
  });
  protected readonly stages = computed(() => this.pipeline.value()?.stages ?? []);

  protected readonly search$ = new Subject<string>();
  protected search = this.route.snapshot.queryParamMap.get('q') ?? '';

  constructor() {
    this.search$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((q) => this.navigate({ q: q.trim() || null }));
  }

  protected navigate(changes: Record<string, string | null>): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: changes,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected slots(r: PriceRecordSummaryResponse): boolean[] {
    const max = Math.max(4, r.agencyCount);
    return Array.from({ length: max }, (_, i) => i < r.agencyCount);
  }

  /** Oferecer a ata: negociação nova com a licitação de origem e o valor dela. */
  protected offer(r: PriceRecordSummaryResponse): void {
    this.presetAgency.set(null);
    this.presetTender.set({
      id: r.tender.id,
      agencyName: r.tender.agencyName,
      identification: r.tender.identification,
      totalValue: r.referenceValue,
      status: r.tender.status,
    });
    this.created.set(null);
    this.dealOpen.set(true);
  }

  protected prospect(agency: AgencyRefResponse): void {
    this.presetTender.set(null);
    this.presetAgency.set(agency);
    this.created.set(null);
    this.dealOpen.set(true);
  }

  protected onDealSaved(title: string | null): void {
    this.dealOpen.set(false);
    this.created.set(title);
  }
}
