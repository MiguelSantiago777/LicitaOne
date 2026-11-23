import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import type {
  GetReportMetric,
  GetReportParams,
  ReportSliceResponse,
} from '../../api/generated/model';
import { ReportsService } from '../../api/generated/reports/reports.service';
import { ApiError } from '../../core/http/api-error';
import { UFS } from '../../shared/format';
import { Icon } from '../../shared/ui/icon';
import { saveBlob } from '../price-records/documents-section';
import { BarList, type BarItem } from './bar-list';
import { CumulativeChart } from './cumulative-chart';
import { MonthChart } from './month-chart';
import { brl, compact, monthName, monthShort, signedPercent } from './report-format';

type Metric = GetReportMetric;

export const METRICS: { id: Metric; label: string }[] = [
  { id: 'PEDIDOS', label: 'Pedidos recebidos' },
  { id: 'CONTRATOS', label: 'Contratos assinados' },
  { id: 'LICITACOES', label: 'Licitações vencidas' },
  { id: 'CARONAS', label: 'Caronas' },
];

const SPHERES = [
  { id: 'MUNICIPAL', label: 'Municipal' },
  { id: 'ESTADUAL', label: 'Estadual' },
  { id: 'FEDERAL', label: 'Federal' },
  { id: 'OUTRA', label: 'Outra' },
];

/**
 * Relatórios para o balanço (Fase 14): um número por vez (pedidos, contratos, licitações vencidas ou caronas) num ano,
 * com cartões, colunas mês a mês, acumulado, comparação entre anos e as quebras. Número, ano, UF e esfera ficam na
 * URL. O PDF sai do servidor com os mesmos números.
 */
@Component({
  selector: 'app-reports-page',
  imports: [FormsModule, Icon, BarList, CumulativeChart, MonthChart],
  templateUrl: './reports.page.html',
  styleUrl: './reports.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportsPage {
  private readonly api = inject(ReportsService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly metrics = METRICS;
  protected readonly spheres = SPHERES;
  protected readonly ufs = UFS;
  protected readonly brl = brl;
  protected readonly compact = compact;
  protected readonly monthName = monthName;
  protected readonly monthShort = monthShort;
  protected readonly signedPercent = signedPercent;
  protected readonly monthNumbers = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];

  private readonly params = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });
  protected readonly metric = computed<Metric>(() => {
    const m = this.params().get('numero') as Metric | null;
    return METRICS.some((x) => x.id === m) ? m! : 'PEDIDOS';
  });
  protected readonly year = computed(() => {
    const y = Number(this.params().get('ano'));
    return Number.isInteger(y) && y > 1999 ? y : new Date().getFullYear();
  });
  protected readonly uf = computed(() => this.params().get('uf') ?? '');
  protected readonly sphere = computed(() => this.params().get('esfera') ?? '');

  protected readonly report = rxResource({
    params: computed((): GetReportParams => ({
      metric: this.metric(),
      year: this.year(),
      state: this.uf() || undefined,
      sphere: this.sphere() || undefined,
    })),
    stream: ({ params }) => this.api.getReport(params),
  });

  /** Anos do seletor: os que o servidor diz ter movimento, e sempre o escolhido. */
  protected readonly years = computed(() => {
    const list = this.report.value()?.availableYears ?? [this.year()];
    return list.includes(this.year()) ? list : [this.year(), ...list].sort((a, b) => b - a);
  });

  /** Algum valor no ano ou no anterior: sem nenhum, o gráfico vazio daria um eixo inventado. */
  protected readonly hasMovement = computed(() =>
    (this.report.value()?.months ?? []).some((m) => m.value > 0 || m.previousYearValue > 0),
  );

  protected readonly unit = computed(() => {
    const r = this.report.value();
    return { singular: r?.singular ?? '', plural: r?.plural ?? '' };
  });

  protected readonly yearBars = computed((): BarItem[] =>
    [...(this.report.value()?.years ?? [])]
      .reverse()
      .map((y) => ({ key: String(y.year), label: String(y.year), value: y.total, count: y.count })),
  );

  protected readonly downloading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected navigate(changes: Record<string, string | number | null>): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: changes,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected bars(slices: ReportSliceResponse[] | undefined): BarItem[] {
    return (slices ?? []).map((s) => ({
      key: s.key ?? null,
      label: s.label,
      detail: s.detail,
      value: s.value,
      count: s.count,
      share: s.share,
    }));
  }

  protected download(): void {
    this.downloading.set(true);
    this.error.set(null);
    const params: GetReportParams = {
      metric: this.metric(),
      year: this.year(),
      state: this.uf() || undefined,
      sphere: this.sphere() || undefined,
    };
    this.api.downloadReportPdf(params, { observe: 'response' }).subscribe({
      next: (response) => {
        this.downloading.set(false);
        const header = response.headers.get('Content-Disposition') ?? '';
        const name =
          /filename\*=UTF-8''([^;]+)/.exec(header)?.[1] ??
          /filename="?([^";]+)"?/.exec(header)?.[1] ??
          `Relatorio-${this.year()}.pdf`;
        saveBlob(response.body as Blob, decodeURIComponent(name));
      },
      error: (err: unknown) => {
        this.downloading.set(false);
        this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível gerar o PDF.');
      },
    });
  }
}
