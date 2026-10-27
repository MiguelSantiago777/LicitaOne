import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TendersService } from '../../api/generated/tenders/tenders.service';
import type { TenderResponseStatus } from '../../api/generated/model';
import { Session } from '../../core/auth/session';
import { daysSince } from '../../shared/format';
import { Avatar } from '../../shared/ui/avatar';
import { Icon } from '../../shared/ui/icon';
import { TENDER_TABS, statusTone } from '../tenders/tender-meta';

function isoDaysAgo(days: number): string {
  const d = new Date(Date.now() - days * 86_400_000);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Painel com os números reais das licitações. Saldo a pedir e atas entram com as Fases 2 e 3. */
@Component({
  selector: 'app-dashboard-page',
  imports: [CurrencyPipe, DatePipe, RouterLink, Icon, Avatar],
  templateUrl: './dashboard.page.html',
  styleUrl: './dashboard.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {
  private readonly api = inject(TendersService);
  private readonly session = inject(Session);
  protected readonly now = new Date();
  protected readonly tone = statusTone;

  protected readonly greeting = computed(() => {
    const hour = this.now.getHours();
    const part = hour < 12 ? 'Bom dia' : hour < 18 ? 'Boa tarde' : 'Boa noite';
    const name = this.session.user()?.name.split(' ')[0];
    return name ? `${part}, ${name}` : part;
  });

  protected readonly summary = rxResource({ stream: () => this.api.tenderSummary() });
  protected readonly stale = rxResource({
    stream: () =>
      this.api.searchTenders({
        status: ['CONTRATO_ASSINADO'],
        signedBefore: isoDaysAgo(30),
        sort: 'signedAt',
        direction: 'asc',
        size: 5,
      }),
  });
  protected readonly upcoming = rxResource({
    stream: () =>
      this.api.searchTenders({
        status: ['EM_DISPUTA'],
        sort: 'sessionAt',
        direction: 'asc',
        size: 5,
      }),
  });
  protected readonly largest = rxResource({
    stream: () =>
      this.api.searchTenders({
        status: ['EM_ANDAMENTO', 'CONTRATO_ASSINADO'],
        sort: 'value',
        direction: 'desc',
        size: 5,
      }),
  });

  private total(statuses: TenderResponseStatus[]) {
    const rows = (this.summary.value() ?? []).filter((s) => statuses.includes(s.status));
    return {
      count: rows.reduce((a, r) => a + r.count, 0),
      value: rows.reduce((a, r) => a + r.value, 0),
    };
  }

  protected readonly portfolio = computed(() => this.total(['CONTRATO_ASSINADO', 'EM_ANDAMENTO']));
  protected readonly awaiting = computed(() => this.total(['VENCIDA', 'AGUARDANDO_ASSINATURA']));
  protected readonly dispute = computed(() => this.total(['EM_DISPUTA']));
  protected readonly running = computed(() => this.total(['EM_ANDAMENTO']));

  protected readonly byTab = computed(() => {
    const rows = TENDER_TABS.filter((t) => t.id !== 'perdidas').map((t) => ({
      tab: t,
      ...this.total(t.statuses),
    }));
    const max = Math.max(1, ...rows.map((r) => r.value));
    return rows.map((r) => ({
      ...r,
      width: Math.max(r.value > 0 ? 2 : 0, Math.round((r.value / max) * 100)),
    }));
  });

  protected readonly tabTone: Record<string, string> = {
    disputa: 'violet',
    vencidas: 'amber',
    assinados: 'blue',
    andamento: 'green',
    encerrados: '',
  };

  protected days(iso: string | null): number {
    return daysSince(iso);
  }
}
