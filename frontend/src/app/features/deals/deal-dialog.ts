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
import { Subject, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { DealsService } from '../../api/generated/deals/deals.service';
import type {
  AgencyRefResponse,
  DealResponse,
  DealTenderResponse,
  PipelineStageResponse,
  TenderResponse,
} from '../../api/generated/model';
import { TendersService } from '../../api/generated/tenders/tenders.service';
import { UsersService } from '../../api/generated/users/users.service';
import { Session } from '../../core/auth/session';
import { ApiError } from '../../core/http/api-error';
import { formatBrlInput, parseBrl } from '../../shared/format';
import { Icon } from '../../shared/ui/icon';
import { Panel } from '../../shared/ui/panel';
import { AgencyPicker } from '../tenders/agency-picker';
import { PRIORITIES } from './deal-meta';

/**
 * Cria ou edita uma negociação: título, órgão prospectado, licitação de origem (ex.: a ata da carona), valor,
 * prioridade, responsável e etapa. Trocar a etapa no diálogo move sem arrastar (teclado e celular).
 */
@Component({
  selector: 'app-deal-dialog',
  imports: [CurrencyPipe, FormsModule, Icon, Panel, AgencyPicker],
  template: `
    <app-panel
      [open]="open()"
      [title]="deal() ? 'Negociação' : 'Nova negociação'"
      [subtitle]="deal()?.stageName ?? null"
      [width]="560"
      (closed)="closed.emit()"
    >
      @if (open()) {
        <form id="deal-form" class="form" (ngSubmit)="save()" novalidate>
          @if (error(); as message) {
            <div class="alert" role="alert">{{ message }}</div>
          }
          @if (readonly()) {
            <p class="hint">Só o responsável ou um administrador altera esta negociação.</p>
          }
          <fieldset class="form" [disabled]="readonly()">
            <div class="field">
              <label for="deal-title">Título</label>
              <input
                id="deal-title"
                class="input"
                name="title"
                [(ngModel)]="title"
                maxlength="200"
                required
                autocomplete="off"
                placeholder="Ex.: Carona da ata de material gráfico"
              />
            </div>

            <div class="field">
              <label for="deal-agency">Órgão</label>
              <app-agency-picker [id]="'deal-agency'" [(value)]="agency" />
            </div>

            <div class="field">
              <label for="deal-tender">Licitação de origem</label>
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
                  id="deal-tender"
                  class="input"
                  name="tenderSearch"
                  [(ngModel)]="tenderSearch"
                  (ngModelChange)="tenderQuery$.next($event)"
                  placeholder="Opcional: a licitação cuja ata você quer replicar"
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
              <p class="hint small">Informe o órgão, a licitação ou os dois.</p>
            </div>

            <div class="grid-2">
              <div class="field">
                <label for="deal-value">Valor (R$)</label>
                <input
                  id="deal-value"
                  class="input num"
                  name="value"
                  inputmode="decimal"
                  [(ngModel)]="value"
                  (blur)="value = formatValue(value)"
                  placeholder="0,00"
                  autocomplete="off"
                />
              </div>
              <div class="field">
                <label for="deal-priority">Prioridade</label>
                <select id="deal-priority" class="input" name="priority" [(ngModel)]="priority">
                  @for (p of priorities; track p.value) {
                    <option [ngValue]="p.value">{{ p.label }}</option>
                  }
                </select>
              </div>
            </div>
            <div class="grid-2">
              <div class="field">
                <label for="deal-owner">Responsável</label>
                <select id="deal-owner" class="input" name="ownerId" [(ngModel)]="ownerId">
                  <option value="">Ninguém</option>
                  @for (o of owners.value() ?? []; track o.id) {
                    <option [value]="o.id">{{ o.name }}</option>
                  }
                </select>
              </div>
              <div class="field">
                <label for="deal-stage">Etapa</label>
                <select id="deal-stage" class="input" name="stageId" [(ngModel)]="stageId">
                  @for (s of stages(); track s.id) {
                    <option [value]="s.id">{{ s.name }}</option>
                  }
                </select>
              </div>
            </div>
            <div class="field">
              <label for="deal-notes">Observações</label>
              <textarea
                id="deal-notes"
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
          <span class="confirm-text">Apagar esta negociação?</span>
          <button type="button" class="btn danger" (click)="remove()" [disabled]="saving()">
            Apagar
          </button>
          <button type="button" class="btn" (click)="confirmingDelete.set(false)">Manter</button>
        } @else {
          @if (deal()?.canEdit) {
            <button type="button" class="btn ghost delete" (click)="confirmingDelete.set(true)">
              Apagar
            </button>
          }
          <button type="button" class="btn" (click)="closed.emit()">
            {{ readonly() ? 'Fechar' : 'Cancelar' }}
          </button>
          @if (!readonly()) {
            <button
              type="submit"
              form="deal-form"
              class="btn primary"
              [disabled]="saving()"
              [attr.aria-busy]="saving() || null"
            >
              {{ saving() ? 'Salvando…' : deal() ? 'Salvar' : 'Criar negociação' }}
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
    .hint.small {
      margin-top: 4px;
      font-size: 12px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DealDialog {
  private readonly api = inject(DealsService);
  private readonly tendersApi = inject(TendersService);
  private readonly usersApi = inject(UsersService);
  private readonly session = inject(Session);

  readonly open = input(false);
  /** Negociação a editar; nula cria uma nova. */
  readonly deal = input<DealResponse | null>(null);
  readonly stages = input<PipelineStageResponse[]>([]);
  /** Etapa de uma negociação nova (o "+" no topo da coluna). */
  readonly initialStageId = input<string | null>(null);
  /** Licitação de origem de uma negociação nova (o "Nova negociação" no detalhe da licitação). */
  readonly presetTender = input<DealTenderResponse | null>(null);
  /** Órgão de uma negociação nova (o "Nova negociação" nos municípios sem ata). */
  readonly presetAgency = input<AgencyRefResponse | null>(null);
  readonly closed = output<void>();
  /** Negociação criada ou alterada; nula quando foi apagada. */
  readonly saved = output<DealResponse | null>();

  protected readonly priorities = PRIORITIES;
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly confirmingDelete = signal(false);
  protected readonly readonly = signal(false);
  protected readonly agency = signal<AgencyRefResponse | null>(null);
  protected readonly tender = signal<DealTenderResponse | null>(null);
  protected readonly tenderQuery$ = new Subject<string>();
  protected readonly tenderResults = signal<TenderResponse[]>([]);
  protected readonly owners = rxResource({ stream: () => this.usersApi.listUserOptions() });

  protected title = '';
  protected value = '';
  protected priority = 2;
  protected ownerId = '';
  protected stageId = '';
  protected notes = '';
  protected tenderSearch = '';

  constructor() {
    effect(() => {
      if (!this.open()) return;
      const d = this.deal();
      this.error.set(null);
      this.confirmingDelete.set(false);
      this.readonly.set(!!d && !d.canEdit);
      this.title = d?.title ?? '';
      const preset = d ? null : this.presetTender();
      this.value = formatBrlInput(d?.value ?? preset?.totalValue ?? null);
      this.priority = d?.priority ?? 2;
      this.ownerId = d ? (d.owner?.id ?? '') : (this.session.user()?.id ?? '');
      this.stageId = d?.stageId ?? this.initialStageId() ?? this.stages()[0]?.id ?? '';
      this.notes = d?.notes ?? '';
      this.agency.set(d?.agency ?? (d ? null : this.presetAgency()));
      this.tender.set(d?.tender ?? preset);
      this.tenderSearch = '';
      this.tenderResults.set([]);
    });
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

  protected formatValue(text: string): string {
    const parsed = parseBrl(text);
    return parsed == null ? text : formatBrlInput(parsed);
  }

  /** Sem valor digitado, a licitação de origem sugere o dela. */
  protected pickTender(t: TenderResponse): void {
    this.tender.set({
      id: t.id,
      agencyName: t.agency.name,
      identification: t.reference,
      totalValue: t.totalValue,
      status: t.status,
    });
    if (!parseBrl(this.value)) this.value = formatBrlInput(t.totalValue);
    this.tenderSearch = '';
    this.tenderResults.set([]);
  }

  protected save(): void {
    if (!this.title.trim()) {
      this.error.set('Informe o título da negociação.');
      return;
    }
    if (!this.agency() && !this.tender()) {
      this.error.set('Informe o órgão ou a licitação de origem.');
      return;
    }
    const value = this.value.trim() ? parseBrl(this.value) : 0;
    if (value == null) {
      this.error.set('Valor inválido. Use o formato 174.000,00.');
      return;
    }
    const body = {
      title: this.title.trim(),
      agencyId: this.agency()?.id,
      tenderId: this.tender()?.id,
      value,
      priority: this.priority,
      ownerId: this.ownerId || undefined,
      notes: this.notes.trim() || undefined,
    };
    const d = this.deal();
    this.saving.set(true);
    this.error.set(null);
    const request = d
      ? this.api.updateDeal(d.id, { version: d.version, deal: body }).pipe(
          switchMap((updated) =>
            this.stageId === d.stageId
              ? of(updated)
              : this.api.moveDeal(d.id, {
                  stageId: this.stageId,
                  position: this.endOf(this.stageId),
                }),
          ),
        )
      : this.api.createDeal({ stageId: this.stageId || undefined, deal: body });
    request.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.saved.emit(saved);
      },
      error: (err: unknown) => this.fail(err),
    });
  }

  protected remove(): void {
    const d = this.deal();
    if (!d) return;
    this.saving.set(true);
    this.api.deleteDeal(d.id, { version: d.version }).subscribe({
      next: () => {
        this.saving.set(false);
        this.saved.emit(null);
      },
      error: (err: unknown) => {
        this.confirmingDelete.set(false);
        this.fail(err);
      },
    });
  }

  private endOf(stageId: string): number {
    return this.stages().find((s) => s.id === stageId)?.dealCount ?? 0;
  }

  private fail(err: unknown): void {
    this.saving.set(false);
    this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível salvar.');
  }
}
