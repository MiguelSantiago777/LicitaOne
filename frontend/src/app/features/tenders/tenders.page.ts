import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { Avatar } from '../../shared/ui/avatar';
import { Icon } from '../../shared/ui/icon';
import {
  DemoTender,
  STATUS_META,
  TENDERS,
  TenderStatus,
  daysBetween,
  sum,
} from '../demo/demo-data';

const ORDER: TenderStatus[] = ['VENCIDA', 'ASSINADO', 'EM_ANDAMENTO', 'ENCERRADO'];

@Component({
  selector: 'app-tenders-page',
  imports: [CurrencyPipe, DatePipe, Icon, Avatar],
  templateUrl: './tenders.page.html',
  styleUrl: './tenders.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TendersPage {
  protected readonly meta = STATUS_META;
  protected readonly selected = signal<TenderStatus>('VENCIDA');
  protected readonly query = signal('');

  protected readonly tabs = ORDER.map((status) => {
    const rows = TENDERS.filter((t) => t.status === status);
    return {
      status,
      label: STATUS_META[status].tab,
      count: rows.length,
      value: sum(rows.map((r) => r.value)),
    };
  });

  protected readonly rows = computed(() => {
    const q = this.query().trim().toLowerCase();
    return TENDERS.filter((t) => t.status === this.selected()).filter(
      (t) => !q || `${t.agency} ${t.city} ${t.uasgPregao} ${t.material}`.toLowerCase().includes(q),
    );
  });
  protected readonly total = computed(() => sum(this.rows().map((r) => r.value)));

  protected share(t: DemoTender): number {
    return Math.round((t.ordered / t.value) * 100);
  }

  protected days(iso: string): number {
    return daysBetween(iso);
  }

  protected ataValid(t: DemoTender): boolean {
    return !!t.ataUntil && daysBetween(t.ataUntil) < 0;
  }

  protected onSearch(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
  }
}
