import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { Icon } from './icon';

@Component({
  selector: 'app-pager',
  imports: [Icon],
  template: `
    @if (total() > 0) {
      <nav class="pager" aria-label="Paginação">
        <span class="range num">{{ first() }}–{{ last() }} de {{ total() }}</span>
        <button
          type="button"
          class="btn sm icon"
          [disabled]="page() === 0"
          (click)="pageChange.emit(page() - 1)"
          aria-label="Página anterior"
        >
          <app-icon name="chevron-right" [size]="16" class="flip" />
        </button>
        <button
          type="button"
          class="btn sm icon"
          [disabled]="last() >= total()"
          (click)="pageChange.emit(page() + 1)"
          aria-label="Próxima página"
        >
          <app-icon name="chevron-right" [size]="16" />
        </button>
      </nav>
    }
  `,
  styles: `
    .pager {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 8px;
      padding: 12px 16px;
      border-top: 1px solid var(--border);
    }
    .range {
      margin-right: 8px;
      color: var(--text-2);
      font-size: 13px;
    }
    .btn.sm.icon {
      width: 30px;
    }
    .flip {
      transform: rotate(180deg);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Pager {
  readonly page = input(0);
  readonly size = input(25);
  readonly total = input(0);
  readonly pageChange = output<number>();
  protected readonly first = computed(() => this.page() * this.size() + 1);
  protected readonly last = computed(() => Math.min((this.page() + 1) * this.size(), this.total()));
}
