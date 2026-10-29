import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { AlertsService } from '../../api/generated/alerts/alerts.service';
import { ContractsService } from '../../api/generated/contracts/contracts.service';
import { PriceRecordsService } from '../../api/generated/price-records/price-records.service';
import { TasksService } from '../../api/generated/tasks/tasks.service';
import { TendersService } from '../../api/generated/tenders/tenders.service';
import type { TenderResponseStatus } from '../../api/generated/model';
import { Session } from '../../core/auth/session';
import { Avatar } from '../../shared/ui/avatar';
import { Icon } from '../../shared/ui/icon';
import { TENDER_TABS, statusTone } from '../tenders/tender-meta';

/**
 * Painel (seção 5.7): carteira por situação, valor em carteira, saldo a pedir, tarefas atrasadas, atas vencendo,
 * contratos sem pedido (com o prazo do alerta), top órgãos e os alertas mais recentes.
 */
@Component({
  selector: 'app-dashboard-page',
  imports: [CurrencyPipe, DatePipe, RouterLink, Icon, Avatar],
  templateUrl: './dashboard.page.html',
  styleUrl: './dashboard.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {
  private readonly api = inject(TendersService);
  private readonly contractsApi = inject(ContractsService);
  private readonly tasksApi = inject(TasksService);
  private readonly recordsApi = inject(PriceRecordsService);
  private readonly alertsApi = inject(AlertsService);
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
  protected readonly openContracts = rxResource({
    stream: () => this.contractsApi.openContractsSummary(),
  });
  /** O prazo de "contrato sem pedido" é o mesmo do alerta (configurável no backend). */
  protected readonly alertSettings = rxResource({ stream: () => this.alertsApi.alertSettings() });
  protected readonly stale = rxResource({
    params: computed(
      () =>
        this.alertSettings.value()?.contractWithoutOrderDays ??
        (this.alertSettings.error() ? 30 : undefined),
    ),
    stream: ({ params: days }) => this.contractsApi.contractsWithoutOrder({ days, limit: 3 }),
  });
  protected readonly tasks = rxResource({ stream: () => this.tasksApi.taskSummary() });
  protected readonly expiring = rxResource({
    stream: () => this.recordsApi.listPriceRecords({ filter: 'VENCENDO' }),
  });
  protected readonly topAgencies = rxResource({
    stream: () => this.api.topAgencies({ limit: 5 }),
  });
  protected readonly recentAlerts = rxResource({
    stream: () => this.alertsApi.searchAlerts({ size: 5 }),
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
  protected readonly maxAgency = computed(() =>
    Math.max(1, ...(this.topAgencies.value() ?? []).map((a) => a.value)),
  );

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

  protected readonly alertTone: Record<string, string> = {
    CONTRATO_SEM_PEDIDO: 'amber',
    ATA_VENCENDO: 'violet',
    LICITACAO_SEM_CONTRATO: 'red',
  };
}
