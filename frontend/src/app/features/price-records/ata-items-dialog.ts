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
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ContractsService } from '../../api/generated/contracts/contracts.service';
import type { ContractResponse, PriceRecordResponse } from '../../api/generated/model';
import { PriceRecordsService } from '../../api/generated/price-records/price-records.service';
import { ApiError } from '../../core/http/api-error';
import { parseQuantity, parseUnitPrice } from '../../shared/format';
import { Icon } from '../../shared/ui/icon';
import { Panel } from '../../shared/ui/panel';
import { formatQty } from './price-record-meta';

interface Row {
  id?: string;
  lot: string;
  description: string;
  unit: string;
  quantity: string;
  unitPrice: string;
  /** Já tem carona: pode mudar, não pode sair. */
  locked: boolean;
}

const price = new Intl.NumberFormat('pt-BR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 4,
});

/**
 * Itens registrados na ata (o quantitativo que limita as caronas). Dá para copiar os itens de um contrato da
 * licitação como ponto de partida. Item que já tem carona pode ser corrigido, mas não removido.
 */
@Component({
  selector: 'app-ata-items-dialog',
  imports: [CurrencyPipe, FormsModule, Icon, Panel],
  template: `
    <app-panel
      [open]="open()"
      title="Itens da ata"
      subtitle="Quantitativo registrado: é a base do limite das caronas."
      [width]="860"
      (closed)="closed.emit()"
    >
      @if (open()) {
        <form id="ata-items-form" class="form" (ngSubmit)="save()" novalidate>
          @if (error(); as message) {
            <div class="alert" role="alert">{{ message }}</div>
          }
          @if (contracts().length) {
            <div class="copy">
              <label for="copy-from">Copiar itens de</label>
              <select id="copy-from" class="input" name="copyFrom" [(ngModel)]="copyFrom">
                <option value="">Escolha um contrato</option>
                @for (c of contracts(); track c.id) {
                  <option [value]="c.id">
                    {{ c.kindLabel }} {{ c.number }} · {{ c.items.length }} itens
                  </option>
                }
              </select>
              <button type="button" class="btn sm" [disabled]="!copyFrom" (click)="copy()">
                Copiar
              </button>
            </div>
          }
          <div class="table-wrap">
            <table class="data rows">
              <caption class="visually-hidden">
                Itens da ata
              </caption>
              <thead>
                <tr>
                  <th scope="col">Lote</th>
                  <th scope="col">Descrição</th>
                  <th scope="col">Un.</th>
                  <th scope="col" class="n">Quantidade</th>
                  <th scope="col" class="n">Valor unitário</th>
                  <th scope="col"><span class="visually-hidden">Remover</span></th>
                </tr>
              </thead>
              <tbody>
                @for (row of rows(); track $index; let i = $index) {
                  <tr>
                    <td>
                      <input
                        class="input lot"
                        [name]="'lot' + i"
                        [(ngModel)]="row.lot"
                        maxlength="40"
                        [attr.aria-label]="'Lote do item ' + (i + 1)"
                      />
                    </td>
                    <td>
                      <input
                        class="input"
                        [name]="'desc' + i"
                        [(ngModel)]="row.description"
                        maxlength="500"
                        [attr.aria-label]="'Descrição do item ' + (i + 1)"
                      />
                    </td>
                    <td>
                      <input
                        class="input unit"
                        [name]="'unit' + i"
                        [(ngModel)]="row.unit"
                        maxlength="20"
                        [attr.aria-label]="'Unidade do item ' + (i + 1)"
                      />
                    </td>
                    <td>
                      <input
                        class="input num n-input"
                        inputmode="decimal"
                        [name]="'qty' + i"
                        [(ngModel)]="row.quantity"
                        [attr.aria-label]="'Quantidade do item ' + (i + 1)"
                      />
                    </td>
                    <td>
                      <input
                        class="input num n-input"
                        inputmode="decimal"
                        [name]="'price' + i"
                        [(ngModel)]="row.unitPrice"
                        [attr.aria-label]="'Valor unitário do item ' + (i + 1)"
                      />
                    </td>
                    <td>
                      @if (row.locked) {
                        <span class="cell-sub" title="Este item já tem carona">Com carona</span>
                      } @else {
                        <button
                          type="button"
                          class="btn ghost icon sm"
                          (click)="remove(i)"
                          [attr.aria-label]="'Remover o item ' + (i + 1)"
                        >
                          <app-icon name="x" [size]="16" />
                        </button>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <div class="foot-row">
            <button type="button" class="btn sm" (click)="add()">
              <app-icon name="plus" [size]="14" />Item
            </button>
            <span class="total"
              >Total registrado: <strong class="num">{{ total() | currency: 'BRL' }}</strong></span
            >
          </div>
        </form>
      }
      <ng-container panel-footer>
        <button type="button" class="btn" (click)="closed.emit()">Cancelar</button>
        <button type="submit" form="ata-items-form" class="btn primary" [disabled]="saving()">
          {{ saving() ? 'Salvando…' : 'Salvar itens' }}
        </button>
      </ng-container>
    </app-panel>
  `,
  styleUrl: './ata-items-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AtaItemsDialog {
  private readonly api = inject(PriceRecordsService);
  private readonly contractsApi = inject(ContractsService);

  readonly open = input(false);
  readonly record = input<PriceRecordResponse | null>(null);
  readonly closed = output<void>();
  readonly saved = output<PriceRecordResponse>();

  protected readonly rows = signal<Row[]>([]);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly contractsRes = rxResource({
    params: () => (this.open() ? this.record()?.tender.id : undefined),
    stream: ({ params }) => this.contractsApi.tenderContracts(params),
  });
  protected readonly contracts = computed(() =>
    (this.contractsRes.value() ?? []).filter((c: ContractResponse) => c.items.length > 0),
  );
  /**
   * Método, não computed: o ngModel muda os campos das linhas sem avisar o signal. Cada tecla roda a detecção de
   * mudanças do diálogo e o total acompanha.
   */
  protected total(): number {
    return this.rows().reduce(
      (sum, r) =>
        sum +
        Math.round((parseQuantity(r.quantity) ?? 0) * (parseUnitPrice(r.unitPrice) ?? 0) * 100) /
          100,
      0,
    );
  }
  protected copyFrom = '';

  constructor() {
    effect(() => {
      if (!this.open()) return;
      const r = this.record();
      this.error.set(null);
      this.copyFrom = '';
      this.rows.set(
        (r?.items ?? []).map((i) => ({
          id: i.id,
          lot: i.lot ?? '',
          description: i.description,
          unit: i.unit ?? '',
          quantity: formatQty(i.quantity),
          unitPrice: price.format(i.unitPrice),
          locked: i.used > 0,
        })),
      );
      if (!r?.items.length) this.add();
    });
  }

  protected add(): void {
    this.rows.update((rows) => [
      ...rows,
      { lot: '', description: '', unit: 'un', quantity: '', unitPrice: '', locked: false },
    ]);
  }

  protected remove(index: number): void {
    this.rows.update((rows) => rows.filter((_, i) => i !== index));
  }

  /** Itens do contrato entram no fim; os da ata que já têm carona continuam. */
  protected copy(): void {
    const contract = this.contracts().find((c) => c.id === this.copyFrom);
    if (!contract) return;
    const kept = this.rows().filter((r) => r.locked || r.description.trim());
    this.rows.set([
      ...kept,
      ...contract.items.map((i) => ({
        lot: i.lot ?? '',
        description: i.description,
        unit: i.unit ?? '',
        quantity: formatQty(i.quantity),
        unitPrice: price.format(i.unitPrice),
        locked: false,
      })),
    ]);
  }

  protected save(): void {
    const r = this.record();
    if (!r) return;
    const filled = this.rows().filter((row) => row.description.trim() || row.locked);
    const items = [];
    for (const [i, row] of filled.entries()) {
      const quantity = parseQuantity(row.quantity);
      const unitPrice = parseUnitPrice(row.unitPrice);
      if (!row.description.trim() || quantity == null || quantity <= 0 || unitPrice == null) {
        this.error.set(`Confira o item ${i + 1}: descrição, quantidade e valor unitário.`);
        return;
      }
      items.push({
        id: row.id,
        lot: row.lot.trim() || undefined,
        description: row.description.trim(),
        unit: row.unit.trim() || undefined,
        quantity,
        unitPrice,
      });
    }
    this.saving.set(true);
    this.error.set(null);
    this.api.replacePriceRecordItems(r.id, { version: r.version, items }).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.saved.emit(updated);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível salvar.');
      },
    });
  }
}
