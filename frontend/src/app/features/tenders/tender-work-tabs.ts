import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { DealsService } from '../../api/generated/deals/deals.service';
import type { DealResponse, TaskResponse, TenderResponse } from '../../api/generated/model';
import { TasksService } from '../../api/generated/tasks/tasks.service';
import { Session } from '../../core/auth/session';
import { Avatar } from '../../shared/ui/avatar';
import { Icon } from '../../shared/ui/icon';
import { DealDialog } from '../deals/deal-dialog';
import { statusLabel } from '../deals/deal-meta';
import { TaskDialog, TaskPreset } from '../tasks/task-dialog';

/** Aba "Tarefas" do detalhe: as tarefas ligadas à licitação, abertas primeiro. */
@Component({
  selector: 'app-tender-tasks-tab',
  imports: [DatePipe, Avatar, Icon, TaskDialog],
  template: `
    <section class="card">
      <header class="card-head">
        <h2>Tarefas</h2>
        @if (canCreate()) {
          <button type="button" class="btn sm" (click)="openNew()">
            <app-icon name="plus" [size]="16" />Nova tarefa
          </button>
        }
      </header>
      <ul class="rows">
        @for (task of tasks(); track task.id) {
          <li [class.done]="task.status === 'CONCLUIDA'">
            <button type="button" class="row-link" (click)="open(task)">{{ task.title }}</button>
            <span class="when num">{{ task.dueAt | date: "dd/MM/yyyy 'às' HH:mm" }}</span>
            @switch (task.status) {
              @case ('CONCLUIDA') {
                <span class="badge green"><span class="dot"></span>Concluída</span>
              }
              @case ('ATRASADA') {
                <span class="badge red"><span class="dot"></span>Atrasada</span>
              }
              @default {
                <span class="badge"><span class="dot"></span>Aberta em dia</span>
              }
            }
            @if (task.owner; as o) {
              <app-avatar [name]="o.name" [size]="24" [attr.title]="o.name" />
            }
          </li>
        } @empty {
          <li class="empty">
            @if (list.isLoading()) {
              Carregando…
            } @else {
              Nenhuma tarefa ligada a esta licitação.
            }
          </li>
        }
      </ul>
    </section>
    <app-task-dialog
      [open]="dialogOpen()"
      [task]="editing()"
      [preset]="preset()"
      (closed)="close()"
      (saved)="close(); list.reload()"
    />
  `,
  styleUrl: './tender-work-tabs.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TenderTasksTab {
  private readonly api = inject(TasksService);
  private readonly session = inject(Session);

  readonly tender = input.required<TenderResponse>();

  protected readonly list = rxResource({
    params: () => this.tender().id,
    stream: ({ params }) =>
      this.api.searchTasks({ tenderId: params, situation: 'TODAS', size: 100 }),
  });
  /** Abertas antes das concluídas; dentro de cada grupo, pelo vencimento. */
  protected readonly tasks = computed(() => {
    const items = this.list.value()?.items ?? [];
    return [
      ...items.filter((t) => t.status !== 'CONCLUIDA'),
      ...items.filter((t) => t.status === 'CONCLUIDA'),
    ];
  });
  protected readonly canCreate = computed(() => this.session.user()?.role !== 'VIEWER');
  protected readonly dialogOpen = signal(false);
  protected readonly editing = signal<TaskResponse | null>(null);
  protected readonly preset = computed((): TaskPreset => {
    const t = this.tender();
    return {
      tender: {
        id: t.id,
        agencyName: t.agency.name,
        identification: t.reference,
        totalValue: t.totalValue,
      },
      ownerId: t.owner?.id,
    };
  });

  protected openNew(): void {
    this.editing.set(null);
    this.dialogOpen.set(true);
  }

  protected open(task: TaskResponse): void {
    this.editing.set(task);
    this.dialogOpen.set(true);
  }

  protected close(): void {
    this.dialogOpen.set(false);
    this.editing.set(null);
  }
}

/** Aba "Negociação" do detalhe: negociações que usam esta licitação como origem (ex.: caronas da ata). */
@Component({
  selector: 'app-tender-deals-tab',
  imports: [CurrencyPipe, RouterLink, Avatar, Icon, DealDialog],
  template: `
    <section class="card">
      <header class="card-head">
        <h2>Negociações</h2>
        <div class="head-actions">
          <a class="btn sm ghost" routerLink="/negociacoes">Abrir o funil</a>
          @if (pipeline.value()?.canCreate) {
            <button type="button" class="btn sm" (click)="openNew()">
              <app-icon name="plus" [size]="16" />Nova negociação
            </button>
          }
        </div>
      </header>
      <ul class="rows">
        @for (deal of deals(); track deal.id) {
          <li>
            <div class="grow">
              <button type="button" class="row-link" (click)="open(deal)">{{ deal.title }}</button>
              <span class="cell-sub"
                >{{ deal.agency?.name ?? 'Mesmo órgão' }} · {{ deal.stageName }}</span
              >
            </div>
            <span
              class="badge"
              [class.blue]="deal.status === 'EM_ANDAMENTO'"
              [class.green]="deal.status === 'GANHA'"
              [class.red]="deal.status === 'PERDIDA'"
              >{{ statusLabel(deal.status) }}</span
            >
            <strong class="num">{{ deal.value | currency: 'BRL' }}</strong>
            @if (deal.owner; as o) {
              <app-avatar [name]="o.name" [size]="24" [attr.title]="o.name" />
            }
          </li>
        } @empty {
          <li class="empty">
            @if (pipeline.isLoading()) {
              Carregando…
            } @else {
              Nenhuma negociação usa esta licitação. Uma carona da ata em outro órgão começa aqui.
            }
          </li>
        }
      </ul>
    </section>
    <app-deal-dialog
      [open]="dialogOpen()"
      [deal]="editing()"
      [stages]="pipeline.value()?.stages ?? []"
      [presetTender]="presetTender()"
      (closed)="close()"
      (saved)="close(); pipeline.reload()"
    />
  `,
  styleUrl: './tender-work-tabs.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TenderDealsTab {
  private readonly api = inject(DealsService);

  readonly tender = input.required<TenderResponse>();

  protected readonly statusLabel = statusLabel;
  protected readonly pipeline = rxResource({
    params: () => this.tender().id,
    stream: ({ params }) => this.api.getPipeline({ tenderId: params }),
  });
  protected readonly deals = computed(() =>
    (this.pipeline.value()?.stages ?? []).flatMap((s) => s.deals),
  );
  protected readonly dialogOpen = signal(false);
  protected readonly editing = signal<DealResponse | null>(null);
  protected readonly presetTender = computed(() => {
    const t = this.tender();
    return {
      id: t.id,
      agencyName: t.agency.name,
      identification: t.reference,
      totalValue: t.totalValue,
      status: t.status,
    };
  });

  protected openNew(): void {
    this.editing.set(null);
    this.dialogOpen.set(true);
  }

  protected open(deal: DealResponse): void {
    this.editing.set(deal);
    this.dialogOpen.set(true);
  }

  protected close(): void {
    this.dialogOpen.set(false);
    this.editing.set(null);
  }
}
