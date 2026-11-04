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
import { FormsModule } from '@angular/forms';
import { ContractsService } from '../../../api/generated/contracts/contracts.service';
import type { ContractResponse, ExtractionItem } from '../../../api/generated/model';
import { ApiError } from '../../../core/http/api-error';
import { formatQuantity, parseQuantity, parseUnitPrice } from '../../../shared/format';
import { Icon } from '../../../shared/ui/icon';
import { Panel } from '../../../shared/ui/panel';

interface ItemRow {
  key: string;
  id?: string;
  lot: string;
  description: string;
  unit: string;
  quantity: string;
  unitPrice: string;
  /** Já pedido: o item não pode sair nem ficar com menos que isso. */
  ordered: number;
}

/** Converte "4,10" ou "0,3333" em número com até 4 casas (valor unitário). */
const price = new Intl.NumberFormat('pt-BR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 4,
});

/** Edita a lista de itens do contrato (descrição, lote, quantidade e valor unitário). */
@Component({
  selector: 'app-items-editor',
  imports: [CurrencyPipe, FormsModule, Icon, Panel],
  template: `
    <app-panel
      [open]="!!contract()"
      title="Itens do contrato"
      [subtitle]="agencyName()"
      [width]="860"
      (closed)="closed.emit()"
    >
      <form id="items-form" class="form" (ngSubmit)="submit()" novalidate>
        @if (error(); as message) {
          <div class="alert" role="alert">{{ message }}</div>
        }
        @if (suggested()?.length) {
          <p class="ai-applied" role="status">
            <span
              ><strong>Itens lidos pela IA no PDF do contrato.</strong> Confira descrição,
              quantidade e valor unitário de cada um antes de salvar; os campos em branco ela não
              conseguiu ler.</span
            >
          </p>
        }
        <div class="table-wrap">
          <table class="items">
            <thead>
              <tr>
                <th scope="col">Lote</th>
                <th scope="col">Descrição</th>
                <th scope="col">Unid.</th>
                <th scope="col" class="r">Quantidade</th>
                <th scope="col" class="r">Valor unitário</th>
                <th scope="col"><span class="visually-hidden">Remover</span></th>
              </tr>
            </thead>
            <tbody>
              @for (row of rows(); track row.key; let i = $index) {
                <tr>
                  <td>
                    <input
                      class="input lot"
                      [name]="'lot-' + row.key"
                      [(ngModel)]="row.lot"
                      maxlength="40"
                      [attr.aria-label]="'Lote do item ' + (i + 1)"
                    />
                  </td>
                  <td>
                    <input
                      class="input"
                      [name]="'desc-' + row.key"
                      [(ngModel)]="row.description"
                      maxlength="500"
                      required
                      [attr.aria-label]="'Descrição do item ' + (i + 1)"
                    />
                  </td>
                  <td>
                    <input
                      class="input unit"
                      [name]="'unit-' + row.key"
                      [(ngModel)]="row.unit"
                      maxlength="20"
                      [attr.aria-label]="'Unidade do item ' + (i + 1)"
                    />
                  </td>
                  <td>
                    <input
                      class="input num-in"
                      inputmode="decimal"
                      [name]="'qty-' + row.key"
                      [(ngModel)]="row.quantity"
                      [attr.aria-label]="'Quantidade do item ' + (i + 1)"
                    />
                    @if (row.ordered > 0) {
                      <span class="hint">{{ fq(row.ordered) }} já pedidos</span>
                    }
                  </td>
                  <td>
                    <input
                      class="input num-in"
                      inputmode="decimal"
                      [name]="'price-' + row.key"
                      [(ngModel)]="row.unitPrice"
                      [attr.aria-label]="'Valor unitário do item ' + (i + 1)"
                    />
                  </td>
                  <td>
                    <button
                      type="button"
                      class="btn ghost icon"
                      (click)="remove(row.key)"
                      [disabled]="row.ordered > 0"
                      [attr.aria-label]="'Remover item ' + (i + 1)"
                      [title]="row.ordered > 0 ? 'Item com pedido não pode sair' : 'Remover'"
                    >
                      <app-icon name="x" [size]="16" />
                    </button>
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="6" class="empty-row">Nenhum item. Adicione abaixo.</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <div class="foot">
          <button type="button" class="btn sm" (click)="add()">
            <app-icon name="plus" [size]="16" />Adicionar item
          </button>
          <span class="cell-sub"
            >Soma dos itens: <strong class="num">{{ total() | currency: 'BRL' }}</strong></span
          >
        </div>
      </form>
      <ng-container panel-footer>
        <button type="button" class="btn" (click)="closed.emit()">Cancelar</button>
        <button
          type="submit"
          form="items-form"
          class="btn primary"
          [disabled]="saving()"
          [attr.aria-busy]="saving() || null"
        >
          {{ saving() ? 'Salvando…' : 'Salvar itens' }}
        </button>
      </ng-container>
    </app-panel>
  `,
  styleUrl: './contract-forms.scss',
  styles: `
    .items {
      width: 100%;
      border-collapse: collapse;
    }
    .items th {
      padding: 0 6px 8px;
      text-align: left;
      font-size: 12px;
      font-weight: 550;
      color: var(--text-3);
      white-space: nowrap;
    }
    .items td {
      padding: 4px 6px;
      vertical-align: top;
    }
    .r {
      text-align: right !important;
    }
    .lot {
      width: 90px;
    }
    .unit {
      width: 70px;
    }
    .num-in {
      width: 120px;
      text-align: right;
      font-variant-numeric: tabular-nums;
    }
    .hint {
      display: block;
      margin-top: 2px;
      text-align: right;
    }
    .empty-row {
      padding: 24px !important;
      text-align: center;
      color: var(--text-3);
    }
    .foot {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      flex-wrap: wrap;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ItemsEditor {
  private readonly api = inject(ContractsService);

  readonly contract = input<ContractResponse | null>(null);
  readonly agencyName = input<string | null>(null);
  /** Itens lidos pela IA (ADR 0016): substituem os itens sem pedido, para a pessoa conferir. */
  readonly suggested = input<ExtractionItem[] | null>(null);
  readonly closed = output<void>();
  readonly saved = output<ContractResponse>();

  protected readonly rows = signal<ItemRow[]>([]);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly fq = formatQuantity;
  private seq = 0;

  /** Recalcula a cada mudança de linha; os inputs editam o objeto da linha, então lê tudo de novo. */
  protected total(): number {
    return this.rows().reduce((sum, r) => {
      const q = parseQuantity(r.quantity);
      const p = parseUnitPrice(r.unitPrice);
      return sum + (q != null && p != null ? Math.round(q * p * 100) / 100 : 0);
    }, 0);
  }

  constructor() {
    effect(() => {
      const c = this.contract();
      if (!c) return;
      this.error.set(null);
      this.rows.set(
        c.items.map((item) => ({
          key: item.id,
          id: item.id,
          lot: item.lot ?? '',
          description: item.description,
          unit: item.unit ?? '',
          quantity: formatQuantity(item.quantity),
          unitPrice: price.format(item.unitPrice),
          ordered: item.ordered,
        })),
      );
      const suggested = this.suggested();
      if (suggested?.length) {
        // Itens com pedido ficam; os demais dão lugar aos lidos
        this.rows.update((rows) => [
          ...rows.filter((row) => row.ordered > 0),
          ...suggested.map((i) => ({
            key: `new-${++this.seq}`,
            lot: i.lot ?? '',
            description: i.description ?? '',
            unit: i.unit ?? '',
            quantity: i.quantity != null ? formatQuantity(i.quantity) : '',
            unitPrice: i.unitPrice != null ? price.format(i.unitPrice) : '',
            ordered: 0,
          })),
        ]);
      } else if (!c.items.length) {
        this.add();
      }
    });
  }

  protected add(): void {
    this.rows.update((rows) => [
      ...rows,
      {
        key: `new-${++this.seq}`,
        lot: '',
        description: '',
        unit: 'un',
        quantity: '',
        unitPrice: '',
        ordered: 0,
      },
    ]);
  }

  protected remove(key: string): void {
    this.rows.update((rows) => rows.filter((r) => r.key !== key));
  }

  protected submit(): void {
    const c = this.contract();
    if (!c) return;
    const items = [];
    for (const [i, r] of this.rows().entries()) {
      const blank = !r.description.trim() && !r.quantity.trim() && !r.unitPrice.trim();
      if (blank && !r.id) continue;
      const quantity = parseQuantity(r.quantity);
      const unitPrice = parseUnitPrice(r.unitPrice);
      if (!r.description.trim()) {
        this.error.set(`Descreva o item ${i + 1}.`);
        return;
      }
      if (quantity == null || quantity <= 0) {
        this.error.set(`Quantidade inválida no item ${i + 1}.`);
        return;
      }
      if (unitPrice == null) {
        this.error.set(`Valor unitário inválido no item ${i + 1} (até 4 casas, como 4,10).`);
        return;
      }
      items.push({
        id: r.id,
        lot: r.lot.trim() || undefined,
        description: r.description.trim(),
        unit: r.unit.trim() || undefined,
        quantity,
        unitPrice,
      });
    }
    this.saving.set(true);
    this.error.set(null);
    const suggestedByAi = !!this.suggested()?.length;
    this.api.replaceContractItems(c.id, { version: c.version, items, suggestedByAi }).subscribe({
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
