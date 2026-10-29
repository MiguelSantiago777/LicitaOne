import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, map, switchMap, takeWhile, timer } from 'rxjs';
import { AdminService } from '../../api/generated/admin/admin.service';
import { AlertsService } from '../../api/generated/alerts/alerts.service';
import type {
  AlertResponse,
  DeadLetterQueueResponse,
  RunAlertsResponse,
  SearchAlertsRule,
} from '../../api/generated/model';
import { Session } from '../../core/auth/session';
import { ApiError } from '../../core/http/api-error';
import { Avatar } from '../../shared/ui/avatar';
import { Icon } from '../../shared/ui/icon';
import { Pager } from '../../shared/ui/pager';

type RuleTab = SearchAlertsRule | 'TODOS';

const TABS: { id: RuleTab; label: string }[] = [
  { id: 'TODOS', label: 'Todos' },
  { id: 'CONTRATO_SEM_PEDIDO', label: 'Contrato sem pedido' },
  { id: 'ATA_VENCENDO', label: 'Ata vencendo' },
  { id: 'LICITACAO_SEM_CONTRATO', label: 'Vencida sem contrato' },
];

const RULE_TONE: Record<string, string> = {
  CONTRATO_SEM_PEDIDO: 'amber',
  ATA_VENCENDO: 'violet',
  LICITACAO_SEM_CONTRATO: 'red',
};

/** Nomes das filas para quem opera, sem o prefixo técnico. */
const QUEUE_LABEL: Record<string, string> = {
  'shemacrm.alerts.create-task': 'Alertas → tarefas',
  'shemacrm.alerts.notify-team': 'Alertas → e-mail da equipe',
  'shemacrm.alerts.contract-facts': 'Alertas → conclusão automática',
  'shemacrm.imports.import-requested': 'Importação de planilhas',
};

export const PAGE_SIZE = 25;

/**
 * Alertas da seção 5.4: as regras com os prazos configurados, o que já foi gerado (tarefa criada e aviso mandado) e,
 * para administradores, "Verificar agora" e as mensagens que falharam (DLQ), com reprocessamento.
 */
@Component({
  selector: 'app-alerts-page',
  imports: [DatePipe, RouterLink, Avatar, Icon, Pager],
  templateUrl: './alerts.page.html',
  styleUrl: './alerts.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlertsPage {
  private readonly api = inject(AlertsService);
  private readonly adminApi = inject(AdminService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly session = inject(Session);

  protected readonly tabs = TABS;
  protected readonly pageSize = PAGE_SIZE;
  protected readonly tone = RULE_TONE;
  protected readonly isAdmin = computed(() => this.session.user()?.role === 'ADMIN');

  private readonly params = toSignal(this.route.queryParamMap.pipe(map((p) => p)), {
    initialValue: this.route.snapshot.queryParamMap,
  });
  protected readonly tab = computed<RuleTab>(() => {
    const value = this.params().get('regra') as RuleTab | null;
    return TABS.some((t) => t.id === value) ? value! : 'TODOS';
  });
  protected readonly page = computed(() => Math.max(0, Number(this.params().get('pagina') ?? 0)));

  protected readonly settings = rxResource({ stream: () => this.api.alertSettings() });
  protected readonly alerts = rxResource({
    params: computed(() => ({
      rule: this.tab() === 'TODOS' ? undefined : (this.tab() as SearchAlertsRule),
      page: this.page(),
      size: PAGE_SIZE,
    })),
    stream: ({ params }) => this.api.searchAlerts(params),
  });
  protected readonly deadLetters = rxResource({
    params: () => (this.isAdmin() ? true : undefined),
    stream: () => this.adminApi.deadLetterQueues(),
  });
  protected readonly failed = computed(() =>
    (this.deadLetters.value() ?? []).reduce((total, q) => total + q.messages, 0),
  );

  protected readonly running = signal(false);
  protected readonly replaying = signal<string | null>(null);
  protected readonly result = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  /** Depois de verificar, a fila cria as tarefas em segundo plano: a lista se atualiza até todas aparecerem. */
  private readonly follow$ = new Subject<void>();

  constructor() {
    this.follow$
      .pipe(
        switchMap(() =>
          timer(800, 1500).pipe(takeWhile((tick) => tick < 8 && this.pending() > 0, true)),
        ),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        this.alerts.reload();
        this.deadLetters.reload();
      });
  }

  /** Alertas da página ainda sem tarefa (a fila ainda não processou). */
  protected readonly pending = computed(
    () => (this.alerts.value()?.items ?? []).filter((a) => !a.taskId).length,
  );

  protected navigate(changes: Record<string, string | null>): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: changes,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected goToPage(page: number): void {
    this.navigate({ pagina: page > 0 ? String(page) : null });
  }

  protected run(): void {
    this.running.set(true);
    this.error.set(null);
    this.result.set(null);
    this.api.runAlerts().subscribe({
      next: (r) => {
        this.running.set(false);
        this.result.set(summary(r));
        this.settings.reload();
        this.alerts.reload();
        this.follow$.next();
      },
      error: (err: unknown) => {
        this.running.set(false);
        this.error.set(
          err instanceof ApiError ? err.detail : 'Não foi possível verificar os alertas.',
        );
      },
    });
  }

  protected replay(q: DeadLetterQueueResponse): void {
    this.replaying.set(q.queue);
    this.error.set(null);
    this.result.set(null);
    this.adminApi.replayDeadLetters(q.queue).subscribe({
      next: (r) => {
        this.replaying.set(null);
        this.result.set(
          r.replayed === 1
            ? '1 mensagem voltou para a fila.'
            : `${r.replayed} mensagens voltaram para a fila.`,
        );
        this.deadLetters.reload();
        this.follow$.next();
      },
      error: (err: unknown) => {
        this.replaying.set(null);
        this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível reprocessar.');
      },
    });
  }

  protected queueLabel(queue: string): string {
    return QUEUE_LABEL[queue] ?? queue;
  }

  protected notice(a: AlertResponse): string {
    if (a.notifiedAt) return `E-mail para ${a.notifiedTo}`;
    if (!this.settings.value()?.mailEnabled) return 'Aviso por e-mail desligado';
    return 'E-mail na fila';
  }
}

function summary(r: RunAlertsResponse): string {
  if (r.raised === 0) return 'Verificação concluída: nenhum alerta novo.';
  const labels: Record<string, string> = {
    CONTRATO_SEM_PEDIDO: 'contrato sem pedido',
    ATA_VENCENDO: 'ata vencendo',
    LICITACAO_SEM_CONTRATO: 'vencida sem contrato',
  };
  const parts = Object.entries(r.byRule ?? {})
    .filter(([, count]) => count > 0)
    .map(([rule, count]) => `${count} ${labels[rule] ?? rule}`);
  const total = r.raised === 1 ? '1 alerta novo' : `${r.raised} alertas novos`;
  return `Verificação concluída: ${total} (${parts.join(', ')}). As tarefas aparecem em instantes.`;
}
