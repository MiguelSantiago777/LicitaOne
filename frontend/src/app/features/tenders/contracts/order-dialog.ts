import { CurrencyPipe } from '@angular/common';
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
import { FormsModule } from '@angular/forms';
import { ContractsService } from '../../../api/generated/contracts/contracts.service';
import type { ContractResponse } from '../../../api/generated/model';
import { ApiError } from '../../../core/http/api-error';
import {
  formatBrlInput,
  formatQuantity,
  parseBrl,
  parseQuantity,
  todayIso,
} from '../../../shared/format';
import { Panel } from '../../../shared/ui/panel';

/**
 * Registra um pedido. Contrato com itens: quantidade por item, limitada ao saldo de cada um, com o total calculado
 * na hora. Contrato sem itens: só o valor.
 */
@Component({
  selector: 'app-order-dialog',
  imports: [CurrencyPipe, FormsModule, Panel],
  template: `
    <app-panel
      [open]="!!contract()"
      title="Registrar pedido"
      [subtitle]="subtitle()"
      [width]="640"
      (closed)="closed.emit()"
    >
      @if (contract(); as c) {
        <form id="order-form" class="form" (ngSubmit)="submit()" novalidate>
          @if (error(); as message) {
            <div class="alert" role="alert">{{ message }}</div>
          }
          <div class="grid-2">
            <div class="field">
              <label for="order-date">Data do pedido</label>
              <input
                id="order-date"
                type="date"
                class="input"
                name="orderedAt"
                [(ngModel)]="orderedAt"
                [min]="c.signedAt"
                [max]="today"
                required
              />
            </div>
            <div class="field">
              <label for="order-ref">Empenho, AF ou ordem de compra</label>
              <input
                id="order-ref"
                class="input"
                name="reference"
                [(ngModel)]="reference"
                maxlength="60"
                placeholder="AF 018/2026"
                autocomplete="off"
              />
            </div>
          </div>

          @if (c.items.length) {
            <fieldset class="lines">
              <legend>Quantidade pedida por item</legend>
              @for (item of c.items; track item.id) {
                <div class="line" [class.done]="item.remaining <= 0">
                  <label [for]="'q-' + item.id">
                    <span class="cell-main">{{ item.description }}</span>
                    <span class="cell-sub num">
                      @if (item.lot) {
                        {{ item.lot }} ·
                      }
                      saldo {{ fq(item.remaining) }} de {{ fq(item.quantity) }} ·
                      {{ item.unitPrice | currency: 'BRL' : 'symbol' : '1.2-4' }} cada
                    </span>
                  </label>
                  <input
                    [id]="'q-' + item.id"
                    class="input qty"
                    inputmode="decimal"
                    [name]="'q-' + item.id"
                    [ngModel]="quantities()[item.id] ?? ''"
                    (ngModelChange)="setQuantity(item.id, $event)"
                    [disabled]="item.remaining <= 0"
                    [attr.aria-invalid]="overBalance(item.id, item.remaining)"
                    placeholder="0"
                    autocomplete="off"
                  />
                </div>
              }
            </fieldset>
            <div class="total-line" aria-live="polite">
              <span>Total do pedido</span>
              <strong>{{ itemsTotal() | currency: 'BRL' }}</strong>
            </div>
          } @else {
            <div class="field">
              <label for="order-value">Valor do pedido</label>
              <div class="money-input">
                <span>R$</span>
                <input
                  id="order-value"
                  class="input"
                  name="value"
                  inputmode="decimal"
                  [(ngModel)]="value"
                  (blur)="value = formatBrlInput(parseBrl(value))"
                  required
                />
              </div>
              <p class="hint">
                Saldo do contrato: {{ c.remaining | currency: 'BRL' }}. Para pedir por item,
                cadastre os itens do contrato.
              </p>
            </div>
          }
          <div class="field">
            <label for="order-notes">Observações</label>
            <textarea
              id="order-notes"
              class="input textarea"
              name="notes"
              rows="2"
              maxlength="2000"
              [(ngModel)]="notes"
            ></textarea>
          </div>
        </form>
      }
      <ng-container panel-footer>
        <button type="button" class="btn" (click)="closed.emit()">Cancelar</button>
        <button
          type="submit"
          form="order-form"
          class="btn primary"
          [disabled]="saving()"
          [attr.aria-busy]="saving() || null"
        >
          {{ saving() ? 'Salvando…' : 'Registrar pedido' }}
        </button>
      </ng-container>
    </app-panel>
  `,
  styleUrl: './contract-forms.scss',
  styles: `
    .lines {
      display: grid;
      gap: 0;
      margin: 0;
      padding: 0;
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
      overflow: hidden;
    }
    .lines legend {
      padding: 0 0 8px;
      font-weight: 550;
      font-size: 13px;
    }
    .line {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 12px;
      border-top: 1px solid var(--border);
    }
    .line:first-of-type {
      border-top: 0;
    }
    .line label {
      display: grid;
      flex: 1;
      min-width: 0;
    }
    .line.done {
      opacity: 0.55;
    }
    .qty {
      width: 110px;
      flex: none;
      text-align: right;
      font-variant-numeric: tabular-nums;
    }
    .qty[aria-invalid='true'] {
      border-color: var(--red);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderDialog {
  private readonly api = inject(ContractsService);

  readonly contract = input<ContractResponse | null>(null);
  readonly agencyName = input<string | null>(null);
  readonly closed = output<void>();
  readonly saved = output<ContractResponse>();

  protected readonly today = todayIso();
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly quantities = signal<Record<string, string>>({});
  protected readonly formatBrlInput = formatBrlInput;
  protected readonly parseBrl = parseBrl;
  protected readonly fq = formatQuantity;

  protected orderedAt = todayIso();
  protected reference = '';
  protected value = '';
  protected notes = '';
  /** Uma chave por abertura do diálogo: clicar duas vezes ou repetir depois de um erro de rede não lança dois pedidos. */
  private idempotencyKey = '';

  protected readonly subtitle = computed(() => {
    const c = this.contract();
    if (!c) return null;
    const name = `${c.kindLabel}${c.number ? ' ' + c.number : ''}`;
    return this.agencyName() ? `${this.agencyName()} · ${name}` : name;
  });

  protected readonly itemsTotal = computed(() => {
    const c = this.contract();
    if (!c) return 0;
    const q = this.quantities();
    return c.items.reduce((sum, item) => {
      const n = parseQuantity(q[item.id]);
      return sum + (n ? Math.round(n * item.unitPrice * 100) / 100 : 0);
    }, 0);
  });

  constructor() {
    effect(() => {
      if (!this.contract()) return;
      this.error.set(null);
      this.quantities.set({});
      this.orderedAt = todayIso();
      this.reference = '';
      this.value = '';
      this.notes = '';
      this.idempotencyKey = crypto.randomUUID();
    });
  }

  protected setQuantity(itemId: string, text: string): void {
    this.quantities.update((q) => ({ ...q, [itemId]: text }));
  }

  protected overBalance(itemId: string, remaining: number): boolean {
    const n = parseQuantity(this.quantities()[itemId]);
    return n != null && n > remaining;
  }

  protected submit(): void {
    const c = this.contract();
    if (!c) return;
    if (!this.orderedAt) {
      this.error.set('Informe a data do pedido.');
      return;
    }
    let lines: { itemId: string; quantity: number }[] | undefined;
    let value: number | undefined;
    if (c.items.length) {
      const q = this.quantities();
      lines = [];
      for (const item of c.items) {
        const text = q[item.id];
        if (!text?.trim()) continue;
        const n = parseQuantity(text);
        if (n == null || n <= 0) {
          this.error.set(`Quantidade inválida em "${item.description}".`);
          return;
        }
        lines.push({ itemId: item.id, quantity: n });
      }
      if (!lines.length) {
        this.error.set('Informe a quantidade pedida de pelo menos um item.');
        return;
      }
    } else {
      const parsed = parseBrl(this.value);
      if (parsed == null || parsed <= 0) {
        this.error.set('Informe o valor do pedido, como 12.000,00.');
        return;
      }
      value = parsed;
    }
    this.saving.set(true);
    this.error.set(null);
    this.api
      .placeContractOrder(
        c.id,
        {
          version: c.version,
          orderedAt: this.orderedAt,
          reference: this.reference.trim() || undefined,
          notes: this.notes.trim() || undefined,
          value,
          lines,
        },
        { headers: { 'Idempotency-Key': this.idempotencyKey } },
      )
      .subscribe({
        next: (result) => {
          this.saving.set(false);
          this.saved.emit(result);
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível salvar.');
        },
      });
  }
}
