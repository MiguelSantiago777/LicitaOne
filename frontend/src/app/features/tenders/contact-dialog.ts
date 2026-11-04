import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { ContractsService } from '../../api/generated/contracts/contracts.service';
import { ExtractionsService } from '../../api/generated/extractions/extractions.service';
import type {
  ResponseReading,
  TenderContactRequestChannel,
  TenderContactRequestOutcome,
  TenderResponse,
} from '../../api/generated/model';
import { TasksService } from '../../api/generated/tasks/tasks.service';
import { TendersService } from '../../api/generated/tenders/tenders.service';
import { UsersService } from '../../api/generated/users/users.service';
import { ApiError } from '../../core/http/api-error';
import { fromLocalDateTimeInput, todayIso } from '../../shared/format';
import { Icon } from '../../shared/ui/icon';
import { Panel } from '../../shared/ui/panel';

const CHANNELS: { value: TenderContactRequestChannel; label: string }[] = [
  { value: 'EMAIL', label: 'E-mail' },
  { value: 'WHATSAPP', label: 'WhatsApp' },
  { value: 'TELEFONE', label: 'Telefone' },
  { value: 'REUNIAO', label: 'Reunião' },
  { value: 'OUTRO', label: 'Outro' },
];

const OUTCOMES: { value: TenderContactRequestOutcome; label: string }[] = [
  { value: 'PEDIDO_PREVISTO', label: 'Pedido previsto' },
  { value: 'SEM_PREVISAO', label: 'Sem previsão' },
  { value: 'PEDIDO_A_CAMINHO', label: 'Pedido a caminho' },
  { value: 'NAO_VAI_PEDIR', label: 'Não vai pedir' },
  { value: 'PEDE_DOCUMENTO', label: 'Pede documento ou informação' },
  { value: 'OUTRO', label: 'Outro' },
];

/** O que o diálogo pede para a tela fazer quando a resposta já é o próprio pedido (Fase 8). */
export interface OrderFromText {
  contractId: string | null;
  text: string;
}

/**
 * Registra um contato com o órgão (Fase 9): canal, data, contrato, situação e o que foi conversado. Com a IA ligada,
 * a resposta colada do órgão é entendida e preenche situação e resumo, e sugere a próxima tarefa. A resposta colada
 * não é guardada; só o resumo que a pessoa salva.
 */
@Component({
  selector: 'app-contact-dialog',
  imports: [DatePipe, FormsModule, Icon, Panel],
  template: `
    <app-panel
      [open]="open()"
      title="Registrar contato"
      [subtitle]="tender().agency.name"
      [width]="560"
      (closed)="closed.emit()"
    >
      @if (open()) {
        <form id="contact-form" class="form" (ngSubmit)="save()" novalidate>
          @if (error(); as message) {
            <div class="alert" role="alert">{{ message }}</div>
          }
          <div class="grid-2">
            <div class="field">
              <label for="contact-channel">Canal</label>
              <select id="contact-channel" class="input" name="channel" [(ngModel)]="channel">
                @for (c of channels; track c.value) {
                  <option [value]="c.value">{{ c.label }}</option>
                }
              </select>
            </div>
            <div class="field">
              <label for="contact-date">Data do contato</label>
              <input
                id="contact-date"
                type="date"
                class="input"
                name="contactedAt"
                [(ngModel)]="contactedAt"
                [max]="today"
                required
              />
            </div>
          </div>

          @if (settings.value()?.enabled && tender().canEdit) {
            <section class="ai-fill" aria-labelledby="contact-ai-title">
              <div class="ai-head">
                <app-icon name="sparkles" [size]="18" />
                <div>
                  <h3 id="contact-ai-title">Entender com IA</h3>
                  <p>
                    Cole a resposta do órgão. A IA preenche a situação e o resumo e sugere a próxima
                    tarefa. A resposta não fica guardada.
                  </p>
                </div>
              </div>
              <label class="visually-hidden" for="contact-response">Resposta do órgão</label>
              <textarea
                id="contact-response"
                class="input textarea"
                name="response"
                rows="4"
                maxlength="20000"
                [ngModel]="response()"
                (ngModelChange)="response.set($event)"
                [disabled]="reading()"
                placeholder="Ex.: Bom dia! O empenho deve sair no mês que vem, depois do orçamento."
              ></textarea>
              <div class="ai-actions">
                <button
                  type="button"
                  class="btn sm primary"
                  (click)="interpret()"
                  [disabled]="reading() || response().trim().length < 5"
                  [attr.aria-busy]="reading() || null"
                >
                  <app-icon name="sparkles" [size]="14" />{{
                    reading() ? 'Lendo…' : 'Entender com IA'
                  }}
                </button>
              </div>
              <div class="ai-status" role="status" aria-live="polite">
                @if (reading()) {
                  <span class="spinner" aria-hidden="true"></span>
                  Lendo a resposta. Leva alguns segundos.
                }
              </div>
            </section>
          }

          @if (aiResult(); as r) {
            <div class="ai-applied" role="status">
              <strong>Situação e resumo preenchidos com a leitura da IA.</strong>
              Confira antes de salvar.
              @if (r.expectedDate) {
                Pedido previsto para {{ r.expectedDate | date: 'dd/MM/yyyy' }}.
              }
              @if (r.warnings?.length) {
                <ul class="ai-warnings">
                  @for (w of r.warnings; track $index) {
                    <li><app-icon name="alert" [size]="14" />{{ w }}</li>
                  }
                </ul>
              }
            </div>
            @if (r.outcome === 'PEDIDO_A_CAMINHO' && canOrder()) {
              <div class="order-hint">
                <span>A resposta parece ser o próprio pedido.</span>
                <button type="button" class="btn sm" (click)="openOrder()">
                  <app-icon name="sparkles" [size]="14" />Registrar pedido com IA
                </button>
              </div>
            }
          }

          <div class="grid-2">
            <div class="field">
              <label for="contact-outcome"
                >Situação
                @if (aiResult()) {
                  <span class="ai-tag">IA</span>
                }
              </label>
              <select id="contact-outcome" class="input" name="outcome" [(ngModel)]="outcome">
                <option value="">Não classificar</option>
                @for (o of outcomes; track o.value) {
                  <option [value]="o.value">{{ o.label }}</option>
                }
              </select>
            </div>
            @if (contracts.value()?.length) {
              <div class="field">
                <label for="contact-contract">Contrato</label>
                <select
                  id="contact-contract"
                  class="input"
                  name="contractId"
                  [(ngModel)]="contractId"
                >
                  <option value="">Nenhum em especial</option>
                  @for (c of contracts.value(); track c.id) {
                    <option [value]="c.id">{{ c.kindLabel }} {{ c.number ?? '' }}</option>
                  }
                </select>
              </div>
            }
          </div>
          <div class="field">
            <label for="contact-summary"
              >O que foi conversado
              @if (aiResult()) {
                <span class="ai-tag">IA</span>
              }
            </label>
            <textarea
              id="contact-summary"
              class="input textarea"
              name="summary"
              rows="3"
              maxlength="1000"
              [(ngModel)]="summary"
              required
              placeholder="Ex.: Pedem em março, depois da liberação do orçamento."
            ></textarea>
          </div>

          <fieldset class="task">
            <legend>
              <label class="check">
                <input type="checkbox" name="withTask" [(ngModel)]="withTask" />
                Criar tarefa para a próxima ação
                @if (aiResult()?.taskTitle) {
                  <span class="ai-tag">IA</span>
                }
              </label>
            </legend>
            @if (withTask) {
              <div class="field">
                <label for="contact-task-title">Tarefa</label>
                <input
                  id="contact-task-title"
                  class="input"
                  name="taskTitle"
                  [(ngModel)]="taskTitle"
                  maxlength="200"
                  autocomplete="off"
                  placeholder="Ex.: Cobrar o pedido de março"
                />
              </div>
              <div class="grid-2">
                <div class="field">
                  <label for="contact-task-due">Para</label>
                  <input
                    id="contact-task-due"
                    type="date"
                    class="input"
                    name="taskDue"
                    [(ngModel)]="taskDue"
                    [min]="today"
                  />
                </div>
                <div class="field">
                  <label for="contact-task-owner">Responsável</label>
                  <select
                    id="contact-task-owner"
                    class="input"
                    name="taskOwner"
                    [(ngModel)]="taskOwner"
                  >
                    @for (u of owners.value() ?? []; track u.id) {
                      <option [value]="u.id">{{ u.name }}</option>
                    }
                  </select>
                </div>
              </div>
            }
          </fieldset>
        </form>
      }
      <ng-container panel-footer>
        <button type="button" class="btn" (click)="closed.emit()">Cancelar</button>
        <button
          type="submit"
          form="contact-form"
          class="btn primary"
          [disabled]="saving()"
          [attr.aria-busy]="saving() || null"
        >
          {{ saving() ? 'Salvando…' : 'Registrar contato' }}
        </button>
      </ng-container>
    </app-panel>
  `,
  styleUrl: '../price-records/document-ai-fill.scss',
  styles: `
    .form {
      display: grid;
      gap: 16px;
    }
    .grid-2 {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 12px;
    }
    .textarea {
      height: auto;
      padding: 10px 12px;
      resize: vertical;
      line-height: 20px;
    }
    .ai-fill .textarea {
      background: var(--surface);
    }
    .ai-actions {
      display: flex;
      justify-content: flex-end;
    }
    .order-hint {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      padding: 10px 14px;
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
      font-size: 13px;
      font-weight: 550;
    }
    .task {
      display: grid;
      gap: 12px;
      margin: 0;
      padding: 12px 14px;
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
    }
    .task legend {
      padding: 0 4px;
    }
    .check {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      font-weight: 550;
      cursor: pointer;
    }
    @media (max-width: 480px) {
      .grid-2 {
        grid-template-columns: minmax(0, 1fr);
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContactDialog {
  private readonly api = inject(TendersService);
  private readonly ai = inject(ExtractionsService);
  private readonly tasksApi = inject(TasksService);
  private readonly contractsApi = inject(ContractsService);
  private readonly usersApi = inject(UsersService);

  readonly open = input(false);
  readonly tender = input.required<TenderResponse>();
  readonly closed = output<void>();
  readonly saved = output<void>();
  /** A resposta já é o pedido: a tela abre "Registrar pedido" com o texto (Fase 8). */
  readonly orderFromText = output<OrderFromText>();

  protected readonly channels = CHANNELS;
  protected readonly outcomes = OUTCOMES;
  protected readonly today = todayIso();
  protected readonly saving = signal(false);
  protected readonly reading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly response = signal('');
  /** Última leitura da IA aplicada; o contato vai como "sugerido pela IA". */
  protected readonly aiResult = signal<ResponseReading | null>(null);

  protected readonly settings = rxResource({ stream: () => this.ai.getExtractionSettings() });
  protected readonly owners = rxResource({
    params: () => (this.open() ? true : undefined),
    stream: () => this.usersApi.listUserOptions(),
  });
  /** Contratos abertos da licitação, para dizer de qual se falou. */
  protected readonly contracts = rxResource({
    params: () => (this.open() ? this.tender().id : undefined),
    stream: ({ params }) =>
      this.contractsApi
        .tenderContracts(params)
        .pipe(switchMap((list) => of(list.filter((c) => !c.closedAt)))),
  });
  protected readonly canOrder = computed(() =>
    (this.contracts.value() ?? []).some((c) => c.items.length && c.canEdit),
  );

  protected channel: TenderContactRequestChannel = 'EMAIL';
  protected contactedAt = todayIso();
  protected outcome: TenderContactRequestOutcome | '' = '';
  protected contractId = '';
  protected summary = '';
  protected withTask = false;
  protected taskTitle = '';
  protected taskDue = '';
  protected taskOwner = '';

  constructor() {
    effect(() => {
      if (!this.open()) return;
      const t = this.tender();
      this.error.set(null);
      this.response.set('');
      this.aiResult.set(null);
      this.channel = 'EMAIL';
      this.contactedAt = todayIso();
      this.outcome = '';
      this.contractId = '';
      this.summary = '';
      this.withTask = false;
      this.taskTitle = '';
      this.taskDue = '';
      // A tarefa sugerida vai para o responsável pela licitação (decisão de 04/11/2026), trocável aqui
      this.taskOwner = t.owner?.id ?? '';
    });
    // Um contrato aberto só: já vem escolhido
    effect(() => {
      const list = this.contracts.value();
      if (list?.length === 1 && !this.contractId) this.contractId = list[0].id;
    });
  }

  protected interpret(): void {
    this.error.set(null);
    this.reading.set(true);
    const channel = CHANNELS.find((c) => c.value === this.channel)?.label;
    this.ai
      .interpretAgencyResponse(this.tender().id, {
        text: this.response().trim(),
        contactedAt: this.contactedAt || undefined,
        channel,
      })
      .subscribe({
        next: (view) => {
          this.reading.set(false);
          this.apply(view.result);
        },
        error: (err: unknown) => {
          this.reading.set(false);
          this.error.set(
            err instanceof ApiError ? err.detail : 'A IA não conseguiu ler a resposta.',
          );
        },
      });
  }

  private apply(r: ResponseReading): void {
    this.outcome = r.outcome as TenderContactRequestOutcome;
    if (r.summary) this.summary = r.summary;
    if (r.taskTitle) {
      this.withTask = true;
      this.taskTitle = r.taskTitle;
      this.taskDue = r.taskDueDate ?? '';
    }
    this.aiResult.set(r);
  }

  protected openOrder(): void {
    this.orderFromText.emit({ contractId: this.contractId || null, text: this.response().trim() });
  }

  protected save(): void {
    if (!this.summary.trim()) {
      this.error.set('Escreva o que foi conversado.');
      return;
    }
    if (!this.contactedAt) {
      this.error.set('Informe a data do contato.');
      return;
    }
    if (this.withTask && (!this.taskTitle.trim() || !this.taskDue)) {
      this.error.set('Dê um título e uma data para a tarefa, ou desmarque "Criar tarefa".');
      return;
    }
    const t = this.tender();
    this.saving.set(true);
    this.error.set(null);
    this.api
      .registerTenderContact(t.id, {
        channel: this.channel,
        contactedAt: this.contactedAt,
        outcome: this.outcome || undefined,
        contractId: this.contractId || undefined,
        summary: this.summary.trim(),
        suggestedByAi: this.aiResult() != null,
      })
      .subscribe({
        next: () => (this.withTask ? this.createTask(t.id) : this.done()),
        error: (err: unknown) => {
          this.saving.set(false);
          this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível salvar.');
        },
      });
  }

  /** O contato já foi salvo: se a tarefa falhar, avisa sem registrar o contato de novo. */
  private createTask(tenderId: string): void {
    this.tasksApi
      .createTask({
        title: this.taskTitle.trim(),
        notes: this.summary.trim(),
        dueAt: fromLocalDateTimeInput(`${this.taskDue}T09:00`)!,
        ownerId: this.taskOwner || undefined,
        tenderId,
      })
      .subscribe({
        next: () => this.done(),
        error: (err: unknown) => {
          this.saving.set(false);
          this.saved.emit();
          const detail = err instanceof ApiError ? err.detail : 'erro inesperado';
          this.error.set(
            `Contato registrado, mas a tarefa não foi criada (${detail}). Crie na aba Tarefas.`,
          );
        },
      });
  }

  private done(): void {
    this.saving.set(false);
    this.saved.emit();
  }
}
