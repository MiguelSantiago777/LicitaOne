import { CurrencyPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Observable, Subject, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { DealsService } from '../../api/generated/deals/deals.service';
import type {
  DealResponse,
  TaskDealResponse,
  TaskResponse,
  TaskTenderResponse,
  TenderResponse,
} from '../../api/generated/model';
import { TasksService } from '../../api/generated/tasks/tasks.service';
import { TendersService } from '../../api/generated/tenders/tenders.service';
import { UsersService } from '../../api/generated/users/users.service';
import { Session } from '../../core/auth/session';
import { ApiError } from '../../core/http/api-error';
import { fromLocalDateTimeInput, toLocalDateTimeInput } from '../../shared/format';
import { Icon } from '../../shared/ui/icon';
import { Panel } from '../../shared/ui/panel';
import { EmailDraft } from './email-draft';

/** O que já vem preenchido numa tarefa nova (ex.: "Criar tarefa" no card da negociação). */
export interface TaskPreset {
  deal?: TaskDealResponse;
  tender?: TaskTenderResponse;
  ownerId?: string;
}

/** Amanhã às 9h: o vencimento mais comum de uma tarefa nova. */
function tomorrowAtNine(): string {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  d.setHours(9, 0, 0, 0);
  return toLocalDateTimeInput(d.toISOString());
}

/**
 * Cria ou edita uma tarefa: título, data e hora, responsável, negociação e licitação ligadas. Na edição, também
 * conclui, reabre e apaga (só as manuais).
 */
@Component({
  selector: 'app-task-dialog',
  imports: [CurrencyPipe, FormsModule, Icon, Panel, EmailDraft],
  template: `
    <app-panel
      [open]="open()"
      [title]="task() ? 'Tarefa' : 'Nova tarefa'"
      [subtitle]="task()?.status === 'CONCLUIDA' ? 'Concluída' : null"
      [width]="560"
      (closed)="closed.emit()"
    >
      @if (open()) {
        <form id="task-form" class="form" (ngSubmit)="save()" novalidate>
          @if (error(); as message) {
            <div class="alert" role="alert">{{ message }}</div>
          }
          @if (task()?.origin === 'ALERTA') {
            <p class="origin">
              <span class="badge violet"
                ><app-icon name="bell" [size]="14" />Criada por alerta</span
              >
              @if (task()!.completedBySystem) {
                <span class="hint"
                  >Concluída sozinha: o que gerou o alerta foi resolvido (pedido ou contrato
                  registrado).</span
                >
              }
            </p>
          }
          @if (task()?.emailDraft; as draft) {
            <app-email-draft [draft]="draft" />
          }
          @if (task() && !task()!.canEdit) {
            <p class="hint">Só o responsável ou um administrador altera esta tarefa.</p>
          }
          <fieldset class="form" [disabled]="readonly()">
            <div class="field">
              <label for="task-title">Tarefa</label>
              <input
                id="task-title"
                class="input"
                name="title"
                [(ngModel)]="title"
                maxlength="200"
                required
                autocomplete="off"
                placeholder="Ex.: Ligar para o setor de compras"
              />
            </div>
            <div class="grid-2">
              <div class="field">
                <label for="task-due">Data e hora</label>
                <input
                  id="task-due"
                  type="datetime-local"
                  class="input"
                  name="dueAt"
                  [(ngModel)]="dueAt"
                  required
                />
              </div>
              <div class="field">
                <label for="task-owner">Responsável</label>
                <select id="task-owner" class="input" name="ownerId" [(ngModel)]="ownerId">
                  <option value="">Ninguém</option>
                  @for (o of owners.value() ?? []; track o.id) {
                    <option [value]="o.id">{{ o.name }}</option>
                  }
                </select>
              </div>
            </div>

            <div class="field">
              <label for="task-deal">Negociação</label>
              @if (deal(); as d) {
                <div class="linked">
                  <div>
                    <span class="cell-main">{{ d.title }}</span>
                    <span class="cell-sub num"
                      >{{ d.agencyName ?? 'Sem órgão' }} · {{ d.value | currency: 'BRL' }}</span
                    >
                  </div>
                  <button
                    type="button"
                    class="btn ghost icon"
                    (click)="deal.set(null)"
                    aria-label="Desligar a negociação"
                  >
                    <app-icon name="x" [size]="16" />
                  </button>
                </div>
              } @else {
                <input
                  id="task-deal"
                  class="input"
                  name="dealSearch"
                  [(ngModel)]="dealSearch"
                  (ngModelChange)="dealQuery$.next($event)"
                  placeholder="Opcional: busque pelo título ou órgão"
                  autocomplete="off"
                />
                @if (dealResults().length) {
                  <ul class="results" aria-label="Negociações encontradas">
                    @for (d of dealResults(); track d.id) {
                      <li>
                        <button type="button" (click)="pickDeal(d)">
                          <span class="cell-main">{{ d.title }}</span>
                          <span class="cell-sub">{{ agencyOf(d) }} · {{ d.stageName }}</span>
                        </button>
                      </li>
                    }
                  </ul>
                }
              }
            </div>

            <div class="field">
              <label for="task-tender">Licitação</label>
              @if (tender(); as t) {
                <div class="linked">
                  <div>
                    <span class="cell-main">{{ t.agencyName }}</span>
                    <span class="cell-sub num"
                      >{{ t.identification }} · {{ t.totalValue | currency: 'BRL' }}</span
                    >
                  </div>
                  <button
                    type="button"
                    class="btn ghost icon"
                    (click)="tender.set(null)"
                    aria-label="Desligar a licitação"
                  >
                    <app-icon name="x" [size]="16" />
                  </button>
                </div>
              } @else {
                <input
                  id="task-tender"
                  class="input"
                  name="tenderSearch"
                  [(ngModel)]="tenderSearch"
                  (ngModelChange)="tenderQuery$.next($event)"
                  placeholder="Opcional: busque por órgão, UASG ou pregão"
                  autocomplete="off"
                />
                @if (tenderResults().length) {
                  <ul class="results" aria-label="Licitações encontradas">
                    @for (t of tenderResults(); track t.id) {
                      <li>
                        <button type="button" (click)="pickTender(t)">
                          <span class="cell-main">{{ t.agency.name }}</span>
                          <span class="cell-sub num">{{ t.reference }} · {{ t.statusLabel }}</span>
                        </button>
                      </li>
                    }
                  </ul>
                }
              }
            </div>

            <div class="field">
              <label for="task-notes">Observações</label>
              <textarea
                id="task-notes"
                class="input textarea"
                name="notes"
                rows="3"
                maxlength="2000"
                [(ngModel)]="notes"
              ></textarea>
            </div>
          </fieldset>
        </form>
      }
      <ng-container panel-footer>
        @if (confirmingDelete()) {
          <span class="confirm-text">Apagar esta tarefa?</span>
          <button type="button" class="btn danger" (click)="remove()" [disabled]="saving()">
            Apagar
          </button>
          <button type="button" class="btn" (click)="confirmingDelete.set(false)">Manter</button>
        } @else {
          @if (task(); as t) {
            @if (t.canEdit && t.origin === 'MANUAL') {
              <button type="button" class="btn ghost delete" (click)="confirmingDelete.set(true)">
                Apagar
              </button>
            }
            @if (t.canEdit) {
              @if (t.status === 'CONCLUIDA') {
                <button type="button" class="btn" (click)="reopen(t)" [disabled]="saving()">
                  Reabrir
                </button>
              } @else {
                <button type="button" class="btn" (click)="complete(t)" [disabled]="saving()">
                  <app-icon name="circle-check" [size]="16" />Concluir
                </button>
              }
            }
          }
          <button type="button" class="btn" (click)="closed.emit()">
            {{ readonly() ? 'Fechar' : 'Cancelar' }}
          </button>
          @if (!readonly()) {
            <button type="submit" form="task-form" class="btn primary" [disabled]="saving()">
              {{ saving() ? 'Salvando…' : task() ? 'Salvar' : 'Criar tarefa' }}
            </button>
          }
        }
      </ng-container>
    </app-panel>
  `,
  styleUrl: '../boards/card-dialog.scss',
  styles: `
    fieldset {
      margin: 0;
      padding: 0;
      border: 0;
      min-width: 0;
    }
    .hint {
      color: var(--text-2);
    }
    .origin {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px 10px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TaskDialog {
  private readonly api = inject(TasksService);
  private readonly dealsApi = inject(DealsService);
  private readonly tendersApi = inject(TendersService);
  private readonly usersApi = inject(UsersService);
  private readonly session = inject(Session);

  readonly open = input(false);
  /** Tarefa a editar; nulo cria uma nova. */
  readonly task = input<TaskResponse | null>(null);
  readonly preset = input<TaskPreset | null>(null);
  readonly closed = output<void>();
  /** Tarefa criada, alterada, concluída ou reaberta; nulo quando foi apagada. */
  readonly saved = output<TaskResponse | null>();

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly confirmingDelete = signal(false);
  protected readonly readonly = signal(false);
  protected readonly deal = signal<TaskDealResponse | null>(null);
  protected readonly tender = signal<TaskTenderResponse | null>(null);
  protected readonly dealQuery$ = new Subject<string>();
  protected readonly tenderQuery$ = new Subject<string>();
  protected readonly dealResults = signal<DealResponse[]>([]);
  protected readonly tenderResults = signal<TenderResponse[]>([]);
  protected readonly owners = rxResource({ stream: () => this.usersApi.listUserOptions() });

  protected title = '';
  protected notes = '';
  protected dueAt = '';
  protected ownerId = '';
  protected dealSearch = '';
  protected tenderSearch = '';

  constructor() {
    effect(() => {
      if (!this.open()) return;
      const t = this.task();
      const p = this.preset();
      this.error.set(null);
      this.confirmingDelete.set(false);
      this.readonly.set(!!t && !t.canEdit);
      this.title = t?.title ?? '';
      this.notes = t?.notes ?? '';
      this.dueAt = t ? toLocalDateTimeInput(t.dueAt) : tomorrowAtNine();
      this.ownerId = t ? (t.owner?.id ?? '') : (p?.ownerId ?? this.session.user()?.id ?? '');
      this.deal.set(t ? (t.deal ?? null) : (p?.deal ?? null));
      this.tender.set(t ? (t.tender ?? null) : (p?.tender ?? null));
      this.dealSearch = '';
      this.tenderSearch = '';
      this.dealResults.set([]);
      this.tenderResults.set([]);
    });
    this.dealQuery$
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((q) =>
          q.trim().length < 2 ? of(null) : this.dealsApi.getPipeline({ q: q.trim() }),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((p) =>
        this.dealResults.set((p?.stages ?? []).flatMap((s) => s.deals).slice(0, 8)),
      );
    this.tenderQuery$
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((q) =>
          q.trim().length < 2 ? of(null) : this.tendersApi.searchTenders({ q: q.trim(), size: 8 }),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((page) => this.tenderResults.set(page?.items ?? []));
  }

  protected agencyOf(d: DealResponse): string {
    return d.agency?.name ?? d.tender?.agencyName ?? 'Sem órgão';
  }

  protected pickDeal(d: DealResponse): void {
    this.deal.set({ id: d.id, title: d.title, agencyName: this.agencyOf(d), value: d.value });
    this.dealSearch = '';
    this.dealResults.set([]);
  }

  protected pickTender(t: TenderResponse): void {
    this.tender.set({
      id: t.id,
      agencyName: t.agency.name,
      identification: t.reference,
      totalValue: t.totalValue,
    });
    this.tenderSearch = '';
    this.tenderResults.set([]);
  }

  protected save(): void {
    if (!this.title.trim()) {
      this.error.set('Informe a tarefa.');
      return;
    }
    const dueAt = fromLocalDateTimeInput(this.dueAt);
    if (!dueAt) {
      this.error.set('Informe a data e a hora.');
      return;
    }
    const body = {
      title: this.title.trim(),
      notes: this.notes.trim() || undefined,
      dueAt,
      ownerId: this.ownerId || undefined,
      dealId: this.deal()?.id,
      tenderId: this.tender()?.id,
    };
    const t = this.task();
    this.run(
      t ? this.api.updateTask(t.id, { version: t.version, task: body }) : this.api.createTask(body),
    );
  }

  protected complete(t: TaskResponse): void {
    this.saving.set(true);
    this.api
      .completeTasks({ ids: [t.id] })
      .pipe(switchMap(() => this.api.getTask(t.id)))
      .subscribe({
        next: (updated) => this.done(updated),
        error: (err: unknown) => this.fail(err),
      });
  }

  protected reopen(t: TaskResponse): void {
    this.run(this.api.reopenTask(t.id, { version: t.version }));
  }

  protected remove(): void {
    const t = this.task();
    if (!t) return;
    this.saving.set(true);
    this.api.deleteTask(t.id, { version: t.version }).subscribe({
      next: () => this.done(null),
      error: (err: unknown) => this.fail(err),
    });
  }

  private run(request: Observable<TaskResponse>): void {
    this.saving.set(true);
    this.error.set(null);
    request.subscribe({
      next: (task) => this.done(task),
      error: (err: unknown) => this.fail(err),
    });
  }

  private done(task: TaskResponse | null): void {
    this.saving.set(false);
    this.saved.emit(task);
  }

  private fail(err: unknown): void {
    this.saving.set(false);
    this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível salvar.');
  }
}
