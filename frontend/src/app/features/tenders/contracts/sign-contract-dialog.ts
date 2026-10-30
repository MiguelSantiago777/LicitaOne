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
import type {
  ContractRequestKind,
  ContractResponse,
  TenderResponse,
} from '../../../api/generated/model';
import { ApiError } from '../../../core/http/api-error';
import { formatBrlInput, parseBrl, todayIso } from '../../../shared/format';
import { Panel } from '../../../shared/ui/panel';

/**
 * Registra um contrato assinado (ou empenho, ou dispensa). O primeiro leva a licitação para "Contrato assinado".
 * Com `contract`, edita os dados do contrato.
 */
@Component({
  selector: 'app-sign-contract-dialog',
  imports: [FormsModule, Panel],
  template: `
    <app-panel
      [open]="open()"
      [title]="contract() ? 'Editar contrato' : 'Registrar contrato'"
      [subtitle]="tender()?.agency?.name ?? null"
      [width]="520"
      (closed)="closed.emit()"
    >
      <form id="contract-form" class="form" (ngSubmit)="submit()" novalidate>
        @if (error(); as message) {
          <div class="alert" role="alert">{{ message }}</div>
        }
        <div class="grid-2">
          <div class="field">
            <label for="contract-kind">Tipo</label>
            <select id="contract-kind" class="input" name="kind" [(ngModel)]="kind">
              <option value="CONTRATO">Contrato</option>
              <option value="EMPENHO">Empenho</option>
              <option value="DISPENSA">Dispensa</option>
            </select>
          </div>
          <div class="field">
            <label for="contract-number">Número</label>
            <input
              id="contract-number"
              class="input"
              name="number"
              [(ngModel)]="number"
              maxlength="40"
              placeholder="12/2026"
              autocomplete="off"
            />
          </div>
          <div class="field">
            <label for="contract-signed">Data de assinatura</label>
            <input
              id="contract-signed"
              type="date"
              class="input"
              name="signedAt"
              [(ngModel)]="signedAt"
              [min]="tender()?.wonAt ?? null"
              [max]="today"
              required
            />
          </div>
          <div class="field">
            <label for="contract-valid">Vigência até</label>
            <input
              id="contract-valid"
              type="date"
              class="input"
              name="validUntil"
              [(ngModel)]="validUntil"
              [min]="signedAt"
            />
          </div>
        </div>
        <div class="field">
          <label for="contract-value">Valor do contrato</label>
          <div class="money-input">
            <span>R$</span>
            <input
              id="contract-value"
              class="input"
              name="value"
              inputmode="decimal"
              [(ngModel)]="value"
              (blur)="value = formatBrlInput(parseBrl(value))"
              required
            />
          </div>
        </div>
        <div class="field">
          <label for="contract-notes">O que foi pedido / o que falta pedir</label>
          <textarea
            id="contract-notes"
            class="input textarea"
            name="notes"
            rows="3"
            maxlength="4000"
            [(ngModel)]="notes"
            placeholder="Anotações livres, como na planilha"
          ></textarea>
        </div>
        @if (!contract()) {
          <p class="note">
            Os itens podem ser cadastrados depois, à mão ou importando a planilha do órgão.
          </p>
        }
      </form>
      <ng-container panel-footer>
        <button type="button" class="btn" (click)="closed.emit()">Cancelar</button>
        <button
          type="submit"
          form="contract-form"
          class="btn primary"
          [disabled]="saving()"
          [attr.aria-busy]="saving() || null"
        >
          {{ saving() ? 'Salvando…' : contract() ? 'Salvar' : 'Registrar contrato' }}
        </button>
      </ng-container>
    </app-panel>
  `,
  styleUrl: './contract-forms.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SignContractDialog {
  private readonly api = inject(ContractsService);

  readonly open = input(false);
  readonly tender = input<TenderResponse | null>(null);
  readonly contract = input<ContractResponse | null>(null);
  readonly closed = output<void>();
  readonly saved = output<ContractResponse>();

  protected readonly today = todayIso();
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly formatBrlInput = formatBrlInput;
  protected readonly parseBrl = parseBrl;

  protected kind: ContractRequestKind = 'CONTRATO';
  protected number = '';
  protected signedAt = todayIso();
  protected validUntil = '';
  protected value = '';
  protected notes = '';

  constructor() {
    effect(() => {
      if (!this.open()) return;
      const c = this.contract();
      const t = this.tender();
      this.error.set(null);
      this.kind = c?.kind ?? 'CONTRATO';
      this.number = c?.number ?? '';
      this.signedAt = c?.signedAt ?? todayIso();
      this.validUntil = c?.validUntil ?? '';
      this.value = formatBrlInput(c?.value ?? t?.totalValue ?? null);
      this.notes = c?.notes ?? '';
    });
  }

  protected submit(): void {
    const t = this.tender();
    if (!t) return;
    const value = parseBrl(this.value);
    if (!this.signedAt) {
      this.error.set('Informe a data de assinatura.');
      return;
    }
    if (value == null) {
      this.error.set('Informe o valor do contrato, como 174.000,00.');
      return;
    }
    const body = {
      kind: this.kind,
      number: this.number.trim() || undefined,
      signedAt: this.signedAt,
      validUntil: this.validUntil || undefined,
      value,
      notes: this.notes.trim() || undefined,
    };
    const c = this.contract();
    const request = c
      ? this.api.updateContract(c.id, { version: c.version, contract: body })
      : this.api.signContract(t.id, { contract: body });
    this.saving.set(true);
    this.error.set(null);
    request.subscribe({
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
