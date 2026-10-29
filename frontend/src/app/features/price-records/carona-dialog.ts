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
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subject, catchError, debounceTime, of, switchMap } from 'rxjs';
import { DealsService } from '../../api/generated/deals/deals.service';
import type {
  AgencyRefResponse,
  CaronaCheckResponse,
  CaronaRequest,
  PriceRecordResponse,
} from '../../api/generated/model';
import { PriceRecordsService } from '../../api/generated/price-records/price-records.service';
import { ApiError } from '../../core/http/api-error';
import { formatBrlInput, parseBrl, parseQuantity, todayIso } from '../../shared/format';
import { Icon } from '../../shared/ui/icon';
import { Panel } from '../../shared/ui/panel';
import { AgencyPicker } from '../tenders/agency-picker';
import { formatQty } from './price-record-meta';

/** O que já vem preenchido (ex.: "Registrar carona" numa negociação ganha). */
export interface CaronaPreset {
  dealId?: string;
  agency?: AgencyRefResponse;
}

/**
 * Registra uma carona: órgão, data, quantidade de cada item (ou o valor, se a ata não tem itens) e a negociação de
 * origem. Confere os limites enquanto digita; passou de algum, só registra marcando "registrar mesmo assim".
 */
@Component({
  selector: 'app-carona-dialog',
  imports: [CurrencyPipe, FormsModule, Icon, Panel, AgencyPicker],
  template: `
    <app-panel
      [open]="open()"
      title="Registrar carona"
      [subtitle]="record() ? 'Ata ' + record()!.number : null"
      [width]="600"
      (closed)="closed.emit()"
    >
      @if (open() && record(); as r) {
        <form id="carona-form" class="form" (ngSubmit)="save(r)" novalidate>
          @if (error(); as message) {
            <div class="alert" role="alert">{{ message }}</div>
          }
          <div class="field">
            <label for="carona-agency">Órgão que aderiu</label>
            <app-agency-picker
              [id]="'carona-agency'"
              [(value)]="agency"
              (valueChange)="recheck()"
            />
          </div>
          <div class="grid-2">
            <div class="field">
              <label for="carona-date">Data da adesão</label>
              <input
                id="carona-date"
                type="date"
                class="input"
                name="adheredAt"
                [(ngModel)]="adheredAt"
                (ngModelChange)="recheck()"
              />
            </div>
            <div class="field">
              <label for="carona-deal">Negociação de origem</label>
              <select id="carona-deal" class="input" name="dealId" [(ngModel)]="dealId">
                <option value="">Nenhuma</option>
                @for (d of deals(); track d.id) {
                  <option [value]="d.id">{{ d.title }} · {{ d.stageName }}</option>
                }
              </select>
            </div>
          </div>

          @if (r.items.length) {
            <fieldset class="lines">
              <legend>Quantidade de cada item</legend>
              @for (item of r.items; track item.id) {
                <div class="line">
                  <label [for]="'carona-qty-' + $index">
                    <span class="cell-main">{{ item.description }}</span>
                    <span class="cell-sub num"
                      >{{ item.unitPrice | currency: 'BRL' : 'symbol' : '1.2-4' }} /
                      {{ item.unit ?? 'un' }} · caronas: {{ qty(item.used) }} de
                      {{ qty(item.limit) }}</span
                    >
                  </label>
                  <input
                    [id]="'carona-qty-' + $index"
                    class="input num qty"
                    inputmode="decimal"
                    [name]="'qty-' + item.id"
                    [(ngModel)]="quantities[item.id]"
                    (ngModelChange)="recheck()"
                    placeholder="0"
                    autocomplete="off"
                  />
                </div>
              }
            </fieldset>
          } @else {
            <div class="field">
              <label for="carona-value">Valor da carona (R$)</label>
              <input
                id="carona-value"
                class="input num"
                inputmode="decimal"
                name="value"
                [(ngModel)]="value"
                (ngModelChange)="recheck()"
                (blur)="value = formatValue(value)"
                placeholder="0,00"
                autocomplete="off"
              />
              <p class="hint">A ata não tem itens: o limite vale sobre o valor.</p>
            </div>
          }

          <div class="field">
            <label for="carona-notes">Observações</label>
            <textarea
              id="carona-notes"
              class="input textarea"
              name="notes"
              rows="2"
              maxlength="2000"
              [(ngModel)]="notes"
            ></textarea>
          </div>

          <div class="summary" aria-live="polite">
            @if (check(); as c) {
              <p>
                Valor da carona: <strong class="num">{{ c.value | currency: 'BRL' }}</strong>
              </p>
              @if (c.warnings.length) {
                <div class="warnings">
                  <p class="w-title">
                    <app-icon name="alert" [size]="16" />Passa dos limites configurados ({{
                      r.limits.maxAgencies
                    }}
                    órgãos e {{ r.limits.maxTotalPercent }}%). Confira com o jurídico antes de
                    registrar.
                  </p>
                  <ul>
                    @for (w of c.warnings; track w.code + $index) {
                      <li>{{ w.message }}</li>
                    }
                  </ul>
                  <label class="confirm">
                    <input type="checkbox" name="confirm" [(ngModel)]="confirmLimits" />
                    Registrar mesmo assim (a carona fica marcada e o histórico registra quem
                    confirmou)
                  </label>
                </div>
              } @else {
                <p class="ok"><app-icon name="circle-check" [size]="16" />Dentro dos limites.</p>
              }
            } @else if (checkError(); as message) {
              <p class="hint">{{ message }}</p>
            }
          </div>
        </form>
      }
      <ng-container panel-footer>
        <button type="button" class="btn" (click)="closed.emit()">Cancelar</button>
        <button
          type="submit"
          form="carona-form"
          class="btn primary"
          [disabled]="saving() || (needsConfirm() && !confirmLimits)"
        >
          {{ saving() ? 'Registrando…' : 'Registrar carona' }}
        </button>
      </ng-container>
    </app-panel>
  `,
  styleUrl: './carona-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CaronaDialog {
  private readonly api = inject(PriceRecordsService);
  private readonly dealsApi = inject(DealsService);

  readonly open = input(false);
  readonly record = input<PriceRecordResponse | null>(null);
  readonly preset = input<CaronaPreset | null>(null);
  readonly closed = output<void>();
  readonly saved = output<PriceRecordResponse>();

  protected readonly qty = formatQty;
  protected readonly agency = signal<AgencyRefResponse | null>(null);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly check = signal<CaronaCheckResponse | null>(null);
  protected readonly checkError = signal<string | null>(null);
  protected readonly needsConfirm = computed(() => (this.check()?.warnings.length ?? 0) > 0);
  private readonly check$ = new Subject<void>();

  /** Negociações desta licitação, para ligar a carona à que a originou. */
  private readonly pipeline = rxResource({
    params: () => (this.open() ? this.record()?.tender.id : undefined),
    stream: ({ params }) => this.dealsApi.getPipeline({ tenderId: params }),
  });
  protected readonly deals = computed(() =>
    (this.pipeline.value()?.stages ?? []).flatMap((s) => s.deals),
  );

  protected adheredAt = '';
  protected dealId = '';
  protected value = '';
  protected notes = '';
  protected quantities: Record<string, string> = {};
  protected confirmLimits = false;

  constructor() {
    effect(() => {
      if (!this.open()) return;
      const p = this.preset();
      this.error.set(null);
      this.check.set(null);
      this.checkError.set(null);
      this.agency.set(p?.agency ?? null);
      this.dealId = p?.dealId ?? '';
      this.adheredAt = todayIso();
      this.value = '';
      this.notes = '';
      this.quantities = {};
      this.confirmLimits = false;
    });
    this.check$
      .pipe(
        debounceTime(300),
        switchMap(() => {
          const r = this.record();
          const body = this.body();
          if (!r || !body) return of(null);
          return this.api.checkCarona(r.id, body).pipe(
            catchError((err: unknown) => {
              this.checkError.set(err instanceof ApiError ? err.detail : null);
              return of(null);
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((c) => {
        this.check.set(c);
        if (c) this.checkError.set(null);
        if (!c?.warnings.length) this.confirmLimits = false;
      });
  }

  protected recheck(): void {
    this.check$.next();
  }

  protected formatValue(text: string): string {
    const parsed = parseBrl(text);
    return parsed == null ? text : formatBrlInput(parsed);
  }

  protected save(r: PriceRecordResponse): void {
    const body = this.body();
    if (!body) {
      this.error.set(this.missing());
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.api.registerCarona(r.id, { carona: body, confirmLimits: this.confirmLimits }).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.saved.emit(updated);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível registrar.');
        this.recheck();
      },
    });
  }

  /** Nulo enquanto falta algo obrigatório (o servidor diria o mesmo). */
  private body(): CaronaRequest | null {
    const r = this.record();
    const agency = this.agency();
    if (!r || !agency || !this.adheredAt) return null;
    const base = {
      agencyId: agency.id,
      adheredAt: this.adheredAt,
      dealId: this.dealId || undefined,
      notes: this.notes.trim() || undefined,
    };
    if (r.items.length) {
      const lines = r.items
        .map((i) => ({ itemId: i.id, quantity: parseQuantity(this.quantities[i.id]) }))
        .filter((l): l is { itemId: string; quantity: number } => !!l.quantity && l.quantity > 0);
      return lines.length ? { ...base, lines } : null;
    }
    const value = parseBrl(this.value);
    return value && value > 0 ? { ...base, value } : null;
  }

  private missing(): string {
    if (!this.agency()) return 'Escolha o órgão que aderiu.';
    if (!this.adheredAt) return 'Informe a data da adesão.';
    return this.record()?.items.length
      ? 'Informe a quantidade de pelo menos um item.'
      : 'Informe o valor da carona.';
  }
}
