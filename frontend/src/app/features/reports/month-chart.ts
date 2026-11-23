import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import type { ReportMonthResponse } from '../../api/generated/model';
import { chartWidth } from './chart-width';
import { axisMonth, brl, compact, monthName, niceMax } from './report-format';

const HEIGHT = 240;
const LEFT = 56;
const BOTTOM = 24;
const TOP = 20;

/**
 * Colunas mês a mês: o ano em azul e o anterior em cinza ao lado (ênfase: um destacado, o outro de contexto). Só o
 * melhor mês leva o valor na ponta; o resto fica no tooltip e na tabela embaixo. Passar o mouse (ou tocar) em
 * qualquer ponto do mês mostra os dois valores.
 */
@Component({
  selector: 'app-month-chart',
  template: `
    <div class="legend" aria-hidden="true">
      <span><i class="key series"></i>{{ year() }}</span>
      <span><i class="key comparison"></i>{{ year() - 1 }}</span>
    </div>
    <div class="plot">
      <svg
        [attr.width]="width()"
        [attr.height]="height"
        role="img"
        [attr.aria-label]="ariaLabel()"
        (pointerleave)="hover.set(null)"
      >
        @for (t of ticks(); track t.y) {
          <line class="grid" [attr.x1]="left" [attr.x2]="width()" [attr.y1]="t.y" [attr.y2]="t.y" />
          <text class="tick" [attr.x]="left - 8" [attr.y]="t.y + 4" text-anchor="end">
            {{ t.label }}
          </text>
        }
        @for (c of columns(); track c.month) {
          <g>
            <rect
              class="hit"
              [attr.x]="c.slotX"
              [attr.y]="top"
              [attr.width]="c.slotW"
              [attr.height]="plotH"
              (pointerenter)="hover.set(c.month)"
              (click)="hover.set(c.month)"
            />
            @if (c.prevH > 0.5) {
              <path class="bar comparison" [attr.d]="c.prevPath" />
            }
            @if (c.h > 0.5) {
              <path
                class="bar series"
                [class.dim]="hover() !== null && hover() !== c.month"
                [attr.d]="c.path"
              />
            }
            <text class="tick" [attr.x]="c.cx" [attr.y]="height - 6" text-anchor="middle">
              {{ c.short }}
            </text>
            @if (c.best && c.h > 0.5) {
              <text
                class="value"
                [attr.x]="c.barX + c.barW / 2"
                [attr.y]="c.y - 6"
                text-anchor="middle"
              >
                {{ c.label }}
              </text>
            }
          </g>
        }
      </svg>
      @if (tooltip(); as t) {
        <div class="tooltip" [style.left.px]="t.x" role="presentation">
          <strong>{{ t.title }}</strong>
          <span><i class="key series"></i>{{ year() }}: {{ t.value }} · {{ t.count }}</span>
          <span><i class="key comparison"></i>{{ year() - 1 }}: {{ t.previous }}</span>
        </div>
      }
    </div>
  `,
  styleUrl: './charts.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MonthChart {
  readonly months = input.required<ReportMonthResponse[]>();
  readonly year = input.required<number>();
  readonly best = input<number | null | undefined>(null);
  readonly unit = input<{ singular: string; plural: string }>({ singular: '', plural: '' });

  protected readonly width = chartWidth();
  protected readonly height = HEIGHT;
  protected readonly left = LEFT;
  protected readonly top = TOP;
  protected readonly plotH = HEIGHT - BOTTOM - TOP;
  protected readonly hover = signal<number | null>(null);

  private readonly scale = computed(() =>
    niceMax(Math.max(0, ...this.months().flatMap((m) => [m.value, m.previousYearValue]))),
  );

  protected readonly ticks = computed(() =>
    [0, 1, 2, 3, 4].map((i) => ({
      y: TOP + this.plotH - (this.plotH * i) / 4,
      label: compact((this.scale() * i) / 4),
    })),
  );

  protected readonly columns = computed(() => {
    const plotW = this.width() - LEFT;
    const slotW = plotW / 12;
    // Colunas finas (até 18 px), com folga entre os meses
    const barW = Math.max(3, Math.min(18, (slotW - 10) / 2));
    const base = TOP + this.plotH;
    return this.months().map((m) => {
      const slotX = LEFT + slotW * (m.month - 1);
      const cx = slotX + slotW / 2;
      const h = (m.value / this.scale()) * this.plotH;
      const prevH = (m.previousYearValue / this.scale()) * this.plotH;
      const barX = cx + 1;
      const prevX = cx - barW - 1;
      return {
        month: m.month,
        short: axisMonth(m.month, slotW),
        slotX,
        slotW,
        cx,
        barX,
        barW,
        h,
        prevH,
        y: base - h,
        path: column(barX, base, barW, h),
        prevPath: column(prevX, base, barW, prevH),
        best: m.month === this.best(),
        label: compact(m.value),
      };
    });
  });

  protected readonly tooltip = computed(() => {
    const month = this.hover();
    if (month === null) return null;
    const m = this.months().find((x) => x.month === month);
    const c = this.columns().find((x) => x.month === month);
    if (!m || !c) return null;
    const u = this.unit();
    // Tooltip de ~190 px: preso dentro do gráfico nas pontas
    const x = Math.min(Math.max(c.cx - 95, 0), this.width() - 190);
    return {
      x,
      title: monthName(month),
      value: brl(m.value),
      count: `${m.count} ${m.count === 1 ? u.singular : u.plural}`,
      previous: brl(m.previousYearValue),
    };
  });

  protected readonly ariaLabel = computed(() => {
    const best = this.best();
    return (
      `Colunas mês a mês de ${this.year()}, com ${this.year() - 1} para comparar.` +
      (best ? ` Melhor mês: ${monthName(best)}.` : '') +
      ' Os valores estão na tabela abaixo.'
    );
  });
}

/** Coluna com a ponta arredondada (4 px) e a base reta. */
function column(x: number, base: number, w: number, h: number): string {
  const r = Math.min(4, w / 2, h);
  const top = base - h;
  return (
    `M${x},${base} V${top + r} Q${x},${top} ${x + r},${top} H${x + w - r} ` +
    `Q${x + w},${top} ${x + w},${top + r} V${base} Z`
  );
}
