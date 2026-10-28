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
import type { ContractResponse } from '../../../api/generated/model';
import { ApiError } from '../../../core/http/api-error';
import { todayIso } from '../../../shared/format';
import { Panel } from '../../../shared/ui/panel';

/** Encerra o contrato com a data de encerramento. O saldo que sobrou fica registrado. */
@Component({
  selector: 'app-close-contract-dialog',
  imports: [CurrencyPipe, FormsModule, Panel],
  template: `
    <app-panel
      variant="dialog"
      [open]="!!contract()"
      title="Encerrar contrato"
      [subtitle]="agencyName()"
      (closed)="closed.emit()"
    >
      @if (contract(); as c) {
        <form id="close-form" class="form" (ngSubmit)="submit()" novalidate>
          @if (error(); as message) {
            <div class="alert" role="alert">{{ message }}</div>
          }
          <p>
            Depois de encerrado, o contrato não recebe mais pedidos.
            @if (c.remaining > 0) {
              Ficam <strong class="num">{{ c.remaining | currency: 'BRL' }}</strong> sem pedido.
            }
          </p>
          <div class="field">
            <label for="close-date">Data de encerramento</label>
            <input
              id="close-date"
              type="date"
              class="input"
              name="closedAt"
              [(ngModel)]="closedAt"
              [min]="c.signedAt"
              [max]="today"
              required
            />
          </div>
          <p class="note">A ata continua valendo depois do contrato; ela entra na Fase 3.</p>
        </form>
      }
      <ng-container panel-footer>
        <button type="button" class="btn" (click)="closed.emit()">Cancelar</button>
        <button type="submit" form="close-form" class="btn primary" [disabled]="saving()">
          {{ saving() ? 'Salvando…' : 'Encerrar' }}
        </button>
      </ng-container>
    </app-panel>
  `,
  styleUrl: './contract-forms.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CloseContractDialog {
  private readonly api = inject(ContractsService);

  readonly contract = input<ContractResponse | null>(null);
  readonly agencyName = input<string | null>(null);
  readonly closed = output<void>();
  readonly saved = output<ContractResponse>();

  protected readonly today = todayIso();
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected closedAt = todayIso();

  constructor() {
    effect(() => {
      if (this.contract()) {
        this.closedAt = todayIso();
        this.error.set(null);
      }
    });
  }

  protected submit(): void {
    const c = this.contract();
    if (!c) return;
    if (!this.closedAt) {
      this.error.set('Informe a data de encerramento.');
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.api.closeContract(c.id, { version: c.version, closedAt: this.closedAt }).subscribe({
      next: (result) => {
        this.saving.set(false);
        this.saved.emit(result);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível encerrar.');
      },
    });
  }
}
