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
import { ContractsService } from '../../../api/generated/contracts/contracts.service';
import { DocumentsService } from '../../../api/generated/documents/documents.service';
import type {
  ContractRequestKind,
  ContractResponse,
  ExtractionItem,
  TenderResponse,
} from '../../../api/generated/model';
import { ApiError } from '../../../core/http/api-error';
import { sameAgency } from '../../../shared/agency-match';
import { formatBrlInput, parseBrl, todayIso } from '../../../shared/format';
import { Icon } from '../../../shared/ui/icon';
import { Panel } from '../../../shared/ui/panel';
import { AiReading, DocumentAiFill } from '../../price-records/document-ai-fill';

type AiField = 'number' | 'signedAt' | 'validUntil' | 'totalValue';

/**
 * Registra um contrato assinado (ou empenho, ou dispensa). O primeiro leva a licitação para "Contrato assinado".
 * Com `contract`, edita os dados do contrato. "Preencher com IA" lê o PDF do contrato (ADR 0016); os itens lidos
 * seguem para o editor de itens depois de salvar.
 */
@Component({
  selector: 'app-sign-contract-dialog',
  imports: [FormsModule, Icon, Panel, DocumentAiFill],
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
        @if (open()) {
          <app-document-ai-fill
            kind="CONTRATO"
            [tenderId]="tender()?.id ?? ''"
            [documents]="documents.value() ?? []"
            (done)="applyAi($event)"
          />
        }
        @if (aiReading(); as r) {
          <div class="ai-applied" role="status">
            <strong>Campos preenchidos com a leitura da IA.</strong>
            Confira cada um no PDF antes de salvar; a página de onde saiu aparece ao lado do campo.
            @if (r.extraction.result?.items?.length; as n) {
              Depois de salvar, os {{ n }} itens lidos aparecem para você conferir.
            }
            @if (aiWarnings().length) {
              <ul class="ai-warnings">
                @for (w of aiWarnings(); track $index) {
                  <li><app-icon name="alert" [size]="14" />{{ w }}</li>
                }
              </ul>
            }
          </div>
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
            <label for="contract-number"
              >Número
              @if (aiTag('number'); as t) {
                <span class="ai-tag">{{ t }}</span>
              }
            </label>
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
            <label for="contract-signed"
              >Data de assinatura
              @if (aiTag('signedAt'); as t) {
                <span class="ai-tag">{{ t }}</span>
              }
            </label>
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
            <label for="contract-valid"
              >Vigência até
              @if (aiTag('validUntil'); as t) {
                <span class="ai-tag">{{ t }}</span>
              }
            </label>
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
          <label for="contract-value"
            >Valor do contrato
            @if (aiTag('totalValue'); as t) {
              <span class="ai-tag">{{ t }}</span>
            }
          </label>
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
        @if (!contract() && !aiReading()?.extraction?.result?.items?.length) {
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
  private readonly documentsApi = inject(DocumentsService);

  readonly open = input(false);
  readonly tender = input<TenderResponse | null>(null);
  readonly contract = input<ContractResponse | null>(null);
  readonly closed = output<void>();
  readonly saved = output<ContractResponse>();
  /** Itens lidos pela IA, para o editor de itens abrir com eles depois de salvar. */
  readonly suggestedItems = output<{ contract: ContractResponse; items: ExtractionItem[] }>();

  protected readonly today = todayIso();
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly formatBrlInput = formatBrlInput;
  protected readonly parseBrl = parseBrl;
  /** Leitura da IA aplicada no formulário aberto; vai como "sugerido pela IA" no histórico. */
  protected readonly aiReading = signal<AiReading | null>(null);

  /** Documentos da licitação, para escolher o PDF do contrato. */
  protected readonly documents = rxResource({
    params: () => (this.open() ? this.tender()?.id : undefined),
    stream: ({ params }) => this.documentsApi.listDocuments({ tenderId: params }),
  });

  /** Avisos da leitura e, se o órgão lido for outro, o aviso de órgão diferente. */
  protected readonly aiWarnings = computed(() => {
    const r = this.aiReading()?.extraction.result;
    if (!r) return [];
    const warnings = [...(r.warnings ?? [])];
    const read = r.agencyName?.value;
    const expected = this.tender()?.agency?.name;
    if (read && expected && !sameAgency(read, expected)) {
      warnings.push(
        `O contrato lido é de "${read}" e esta licitação é de ${expected}. Confira se o PDF é deste contrato.`,
      );
    }
    return warnings;
  });

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
      this.aiReading.set(null);
      this.kind = c?.kind ?? 'CONTRATO';
      this.number = c?.number ?? '';
      this.signedAt = c?.signedAt ?? todayIso();
      this.validUntil = c?.validUntil ?? '';
      this.value = formatBrlInput(c?.value ?? t?.totalValue ?? null);
      this.notes = c?.notes ?? '';
    });
  }

  /** Preenche com o que a IA leu. Só troca os campos que ela achou; o resto fica como está. */
  protected applyAi(reading: AiReading): void {
    const r = reading.extraction.result;
    if (r?.number?.value) this.number = r.number.value.slice(0, 40);
    if (r?.signedAt?.value) this.signedAt = r.signedAt.value;
    if (r?.validUntil?.value) this.validUntil = r.validUntil.value;
    if (r?.totalValue?.value != null) this.value = formatBrlInput(r.totalValue.value);
    this.error.set(null);
    this.aiReading.set(reading);
  }

  /** Etiqueta "IA · p. 3" ao lado do campo que veio da leitura. */
  protected aiTag(field: AiField): string | null {
    const v = this.aiReading()?.extraction.result?.[field];
    if (v?.value == null) return null;
    return v.page ? `IA · p. ${v.page}` : 'IA';
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
    const reading = this.aiReading();
    const suggestedByAi = reading != null;
    const c = this.contract();
    const request = c
      ? this.api.updateContract(c.id, { version: c.version, contract: body, suggestedByAi })
      : this.api.signContract(t.id, { contract: body, suggestedByAi });
    this.saving.set(true);
    this.error.set(null);
    request.subscribe({
      next: (result) => {
        this.saving.set(false);
        this.saved.emit(result);
        const items = reading?.extraction.result?.items ?? [];
        if (items.length) this.suggestedItems.emit({ contract: result, items });
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível salvar.');
      },
    });
  }
}
