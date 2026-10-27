import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Session } from '../../core/auth/session';
import { Avatar } from '../../shared/ui/avatar';
import { Icon } from '../../shared/ui/icon';
import {
  DEALS,
  STATUS_META,
  TASKS,
  TENDERS,
  TenderStatus,
  daysBetween,
  sum,
  today,
} from '../demo/demo-data';

@Component({
  selector: 'app-dashboard-page',
  imports: [CurrencyPipe, DatePipe, RouterLink, Icon, Avatar],
  templateUrl: './dashboard.page.html',
  styleUrl: './dashboard.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {
  private readonly session = inject(Session);
  protected readonly now = today();

  protected readonly greeting = computed(() => {
    const hour = this.now.getHours();
    const part = hour < 12 ? 'Bom dia' : hour < 18 ? 'Boa tarde' : 'Boa noite';
    const name = this.session.user()?.name.split(' ')[0];
    return name ? `${part}, ${name}` : part;
  });

  private readonly active = TENDERS.filter(
    (t) => t.status === 'ASSINADO' || t.status === 'EM_ANDAMENTO',
  );
  protected readonly portfolio = sum(this.active.map((t) => t.value));
  protected readonly toOrder = sum(this.active.map((t) => t.value - t.ordered));
  protected readonly orderedShare = Math.round(
    ((this.portfolio - this.toOrder) / this.portfolio) * 100,
  );

  private readonly won = TENDERS.filter((t) => t.status === 'VENCIDA');
  protected readonly wonCount = this.won.length;
  protected readonly wonValue = sum(this.won.map((t) => t.value));

  protected readonly validRecords = TENDERS.filter(
    (t) => t.ataUntil && daysBetween(t.ataUntil) < 0,
  );
  protected readonly expiringRecords = this.validRecords.filter(
    (t) => -daysBetween(t.ataUntil!) <= 120,
  ).length;

  /** Contratos assinados há mais de 30 dias sem nenhum pedido: pedem e-mail ao órgão. */
  protected readonly stale = TENDERS.filter(
    (t) => t.status === 'ASSINADO' && t.ordered === 0 && daysBetween(t.signedAt!) > 30,
  ).map((t) => ({ ...t, days: daysBetween(t.signedAt!) }));

  protected readonly byStatus = (
    ['VENCIDA', 'ASSINADO', 'EM_ANDAMENTO', 'ENCERRADO'] as TenderStatus[]
  ).map((status) => {
    const rows = TENDERS.filter((t) => t.status === status);
    return {
      status,
      label: STATUS_META[status].tab,
      tone: STATUS_META[status].tone,
      count: rows.length,
      value: sum(rows.map((r) => r.value)),
    };
  });
  private readonly maxStatusValue = Math.max(...this.byStatus.map((s) => s.value));
  protected barWidth(value: number): number {
    return Math.max(2, Math.round((value / this.maxStatusValue) * 100));
  }

  protected readonly balances = TENDERS.filter((t) => t.status === 'EM_ANDAMENTO')
    .map((t) => ({
      ...t,
      share: Math.round((t.ordered / t.value) * 100),
      left: t.value - t.ordered,
    }))
    .sort((a, b) => b.left - a.left);

  protected readonly overdue = TASKS.filter((t) => !t.done && daysBetween(t.dueAt) > 0).slice(0, 4);
  protected readonly pipelineValue = sum(DEALS.map((d) => d.value));
  protected readonly pipelineCount = DEALS.length;
}
