import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { brl, percent } from './report-format';

export interface BarItem {
  key: string | null;
  label: string;
  detail?: string | null;
  value: number;
  count: number;
  share?: number | null;
}

/**
 * Barras horizontais em lista: nome, barra e o valor na ponta. "Outros" (sem chave) fica em cinza. Feito em HTML e
 * não em SVG: nomes longos quebram linha e a lista é lida como lista pelo leitor de tela.
 */
@Component({
  selector: 'app-bar-list',
  template: `
    @if (items().length) {
      <ol class="bars">
        @for (b of bars(); track b.label) {
          <li>
            <div class="row-head">
              <span class="name">
                {{ b.label }}
                @if (b.detail) {
                  <span class="detail">{{ b.detail }}</span>
                }
              </span>
              <span class="amount num">
                {{ money(b.value) }}
                @if ((b.share ?? null) !== null) {
                  <span class="detail">{{ pct(b.share) }}</span>
                }
              </span>
            </div>
            <div class="track" aria-hidden="true">
              <span class="fill" [class.others]="b.key === null" [style.width.%]="b.width"></span>
            </div>
            <span class="visually-hidden"
              >{{ b.count }} {{ b.count === 1 ? singular() : plural() }}</span
            >
          </li>
        }
      </ol>
    } @else {
      <p class="empty-note">{{ emptyText() }}</p>
    }
  `,
  styles: `
    .bars {
      display: grid;
      gap: 12px;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .row-head {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
      gap: 12px;
      margin-bottom: 5px;
      font-size: 13px;
    }
    .name {
      min-width: 0;
      color: var(--text);
      font-weight: 550;
    }
    .detail {
      margin-left: 6px;
      color: var(--text-3);
      font-weight: 400;
      font-size: 12px;
    }
    .amount {
      flex: none;
      color: var(--text);
      font-weight: 600;
      white-space: nowrap;
    }
    .track {
      height: 8px;
      border-radius: 999px;
      background: var(--hover);
      overflow: hidden;
    }
    .fill {
      display: block;
      height: 100%;
      min-width: 2px;
      border-radius: 999px;
      background: var(--brand);
    }
    .fill.others {
      background: #94a3b8;
    }
    .empty-note {
      margin: 0;
      color: var(--text-3);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BarList {
  readonly items = input.required<BarItem[]>();
  readonly singular = input('');
  readonly plural = input('');
  readonly emptyText = input('Sem movimento no período.');

  protected readonly money = brl;
  protected readonly pct = percent;

  protected readonly bars = computed(() => {
    const max = Math.max(0, ...this.items().map((i) => i.value));
    return this.items().map((i) => ({ ...i, width: max === 0 ? 0 : (i.value / max) * 100 }));
  });
}
