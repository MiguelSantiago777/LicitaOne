import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import type { ReportMonthResponse } from '../../api/generated/model';
import { chartWidth } from './chart-width';
import { axisMonth, brl, compact, monthName, niceMax } from './report-format';

const HEIGHT = 200;
const LEFT = 56;
const BOTTOM = 24;
const TOP = 20;

/**
 * Acumulado do ano em linha (2 px), com o ano anterior em cinza. A linha do ano para no último mês com dado, e só a
 * ponta leva o valor. Passar o mouse mostra a linha vertical e os dois acumulados do mês.
 */
@Component({
  selector: 'app-cumulative-chart',
  template: `
    <div class="legend" aria-hidden="true">
      <span><i class="key line series"></i>{{ year() }}</span>
      <span><i class="key line comparison"></i>{{ year() - 1 }}</span>
    </div>
    <div class="plot">
      <svg
        [attr.width]="width()"
        [attr.height]="height"
        role="img"
        [attr.aria-label]="ariaLabel()"
        (pointermove)="move($event)"
        (pointerleave)="hover.set(null)"
      >
        @for (t of ticks(); track t.y) {
          <line class="grid" [attr.x1]="left" [attr.x2]="width()" [attr.y1]="t.y" [attr.y2]="t.y" />
          <text class="tick" [attr.x]="left - 8" [attr.y]="t.y + 4" text-anchor="end">
            {{ t.label }}
          </text>
        }
        @for (p of points(); track p.month) {
          <text class="tick" [attr.x]="p.x" [attr.y]="height - 6" text-anchor="middle">
            {{ p.short }}
          </text>
        }
        @if (hoverPoint(); as h) {
          <line
            class="crosshair"
            [attr.x1]="h.x"
            [attr.x2]="h.x"
            [attr.y1]="top"
            [attr.y2]="top + plotH"
          />
        }
        <path class="line comparison" [attr.d]="previousPath()" />
        <path class="line series" [attr.d]="currentPath()" />
        @if (end(); as e) {
          <circle class="dot" [attr.cx]="e.x" [attr.cy]="e.y" r="4" />
          <text class="value" [attr.x]="e.x" [attr.y]="e.y - 10" [attr.text-anchor]="e.anchor">
            {{ e.label }}
          </text>
        }
        @if (hoverPoint(); as h) {
          @if (h.y !== null) {
            <circle class="dot" [attr.cx]="h.x" [attr.cy]="h.y" r="4" />
          }
        }
      </svg>
      @if (tooltip(); as t) {
        <div class="tooltip" [style.left.px]="t.x" role="presentation">
          <strong>Até {{ t.title }}</strong>
          <span><i class="key line series"></i>{{ year() }}: {{ t.value }}</span>
          <span><i class="key line comparison"></i>{{ year() - 1 }}: {{ t.previous }}</span>
        </div>
      }
    </div>
  `,
  styleUrl: './charts.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CumulativeChart {
  readonly months = input.required<ReportMonthResponse[]>();
  readonly year = input.required<number>();

  protected readonly width = chartWidth();
  protected readonly height = HEIGHT;
  protected readonly left = LEFT;
  protected readonly top = TOP;
  protected readonly plotH = HEIGHT - BOTTOM - TOP;
  protected readonly hover = signal<number | null>(null);

  private readonly scale = computed(() =>
    niceMax(
      Math.max(0, ...this.months().flatMap((m) => [m.cumulative ?? 0, m.previousYearCumulative])),
    ),
  );

  protected readonly ticks = computed(() =>
    [0, 1, 2, 3, 4].map((i) => ({
      y: TOP + this.plotH - (this.plotH * i) / 4,
      label: compact((this.scale() * i) / 4),
    })),
  );

  private readonly slot = computed(() => (this.width() - LEFT) / 12);

  protected readonly points = computed(() =>
    this.months().map((m) => {
      const x = LEFT + this.slot() * (m.month - 1) + this.slot() / 2;
      return {
        month: m.month,
        short: axisMonth(m.month, this.slot()),
        x,
        y: m.cumulative == null ? null : this.yOf(m.cumulative),
        py: this.yOf(m.previousYearCumulative),
      };
    }),
  );

  protected readonly currentPath = computed(() =>
    path(
      this.points()
        .filter((p) => p.y !== null)
        .map((p) => [p.x, p.y as number]),
    ),
  );
  protected readonly previousPath = computed(() => path(this.points().map((p) => [p.x, p.py])));

  protected readonly end = computed(() => {
    const withValue = this.points().filter((p) => p.y !== null);
    const last = withValue[withValue.length - 1];
    if (!last) return null;
    const m = this.months().find((x) => x.month === last.month)!;
    return {
      x: last.x,
      y: last.y as number,
      label: compact(m.cumulative ?? 0),
      anchor: last.month >= 11 ? 'end' : 'middle',
    };
  });

  protected readonly hoverPoint = computed(() => {
    const month = this.hover();
    return month === null ? null : (this.points().find((p) => p.month === month) ?? null);
  });

  protected readonly tooltip = computed(() => {
    const p = this.hoverPoint();
    if (!p) return null;
    const m = this.months().find((x) => x.month === p.month)!;
    return {
      x: Math.min(Math.max(p.x - 95, 0), this.width() - 190),
      title: monthName(p.month).toLowerCase(),
      value: m.cumulative == null ? '—' : brl(m.cumulative),
      previous: brl(m.previousYearCumulative),
    };
  });

  protected readonly ariaLabel = computed(
    () =>
      `Acumulado de ${this.year()} mês a mês, com ${this.year() - 1} para comparar.` +
      (this.end() ? ` Até agora: ${this.end()!.label}.` : '') +
      ' Os valores estão na tabela abaixo.',
  );

  protected move(event: PointerEvent): void {
    const svg = event.currentTarget as SVGSVGElement;
    const x = event.clientX - svg.getBoundingClientRect().left;
    if (x < LEFT) {
      this.hover.set(null);
      return;
    }
    const month = Math.min(12, Math.max(1, Math.floor((x - LEFT) / this.slot()) + 1));
    this.hover.set(month);
  }

  private yOf(value: number): number {
    return TOP + this.plotH - (value / this.scale()) * this.plotH;
  }
}

function path(points: number[][]): string {
  return points.map(([x, y], i) => `${i === 0 ? 'M' : 'L'}${x},${y}`).join(' ');
}
