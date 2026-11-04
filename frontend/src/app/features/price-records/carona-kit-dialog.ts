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
import { DocumentsService } from '../../api/generated/documents/documents.service';
import { ExtractionsService } from '../../api/generated/extractions/extractions.service';
import type {
  AgencyRefResponse,
  CaronaKitTextsResponse,
  CaronaLetterResponse,
  CaronaOfferResponse,
  PriceRecordResponse,
} from '../../api/generated/model';
import { ApiError } from '../../core/http/api-error';
import { formatBrlInput, parseBrl, parseQuantity } from '../../shared/format';
import { Icon } from '../../shared/ui/icon';
import { Panel } from '../../shared/ui/panel';
import { AgencyPicker } from '../tenders/agency-picker';
import { saveBlob } from './documents-section';
import { formatQty } from './price-record-meta';

type LetterKind = 'APRESENTACAO' | 'SOLICITACAO' | 'ANUENCIA';

/** Texto editável de uma carta do kit. */
interface Draft {
  kind: LetterKind;
  title: string;
  subject: string;
  body: string;
}

/**
 * Kit de carona (Fase 10): para um município candidato, mostra quanto ele pode pedir de cada item (50% por órgão, sem
 * passar do total) e gera a carta de apresentação, o modelo de ofício de solicitação e o ofício de anuência. A IA
 * escreve só a carta e a justificativa; a anuência é sempre o modelo fixo. O LicitaOne não envia nada.
 */
@Component({
  selector: 'app-carona-kit-dialog',
  imports: [CurrencyPipe, FormsModule, Icon, Panel, AgencyPicker],
  template: `
    <app-panel
      [open]="open()"
      title="Kit de carona"
      [subtitle]="record() ? 'Ata ' + record()!.number : null"
      [width]="760"
      (closed)="closed.emit()"
    >
      @if (open() && record(); as r) {
        <div class="kit">
          <div class="field">
            <label for="kit-agency">Município candidato</label>
            <app-agency-picker
              [id]="'kit-agency'"
              [value]="agency()"
              (valueChange)="pick($event)"
            />
          </div>

          @if (error(); as message) {
            <div class="alert" role="alert">{{ message }}</div>
          }
          @if (loading()) {
            <p class="hint" role="status">Conferindo o que dá para oferecer…</p>
          }

          @if (offer(); as o) {
            @if (o.offer.warnings.length || o.companyMissing.length) {
              <ul class="warnings" aria-label="Confira antes de mandar">
                @for (w of o.offer.warnings; track w.code) {
                  <li><app-icon name="alert" [size]="14" />{{ w.message }}</li>
                }
                @if (o.companyMissing.length) {
                  <li>
                    <app-icon name="alert" [size]="14" />Faltam dados da empresa ({{
                      o.companyMissing.join(', ')
                    }}). Os textos mostram o campo entre colchetes; configure SHEMA_COMPANY_* no
                    ambiente.
                  </li>
                }
              </ul>
            }

            @if (o.offer.items.length) {
              <fieldset class="lines">
                <legend>
                  Quanto oferecer (até {{ o.offer.maxPerAgencyPercent }}% de cada item por
                  município)
                </legend>
                @for (item of o.offer.items; track item.itemId) {
                  <div class="line">
                    <label [for]="'kit-qty-' + $index">
                      <span class="cell-main">{{ item.description }}</span>
                      <span class="cell-sub num"
                        >{{ item.unitPrice | currency: 'BRL' : 'symbol' : '1.2-4' }} /
                        {{ item.unit ?? 'un' }} · pode até {{ qty(item.maxQuantity) }}</span
                      >
                    </label>
                    <input
                      [id]="'kit-qty-' + $index"
                      class="input num qty"
                      inputmode="decimal"
                      [name]="'kit-qty-' + item.itemId"
                      [(ngModel)]="quantities[item.itemId]"
                      placeholder="0"
                      autocomplete="off"
                    />
                  </div>
                }
              </fieldset>
            } @else {
              <div class="field">
                <label for="kit-value">Valor oferecido (R$)</label>
                <input
                  id="kit-value"
                  class="input num"
                  inputmode="decimal"
                  name="kitValue"
                  [(ngModel)]="value"
                  (blur)="value = formatValue(value)"
                  autocomplete="off"
                />
                <p class="hint">
                  A ata não tem itens: o município pode aderir até
                  {{ o.offer.maxValue ?? 0 | currency: 'BRL' }}.
                </p>
              </div>
            }

            <div class="gen">
              <div class="ai-status" role="status" aria-live="polite">
                @if (busy()) {
                  <span class="spinner" aria-hidden="true"></span>
                  {{ busyAi() ? 'A IA está escrevendo. Leva alguns segundos.' : 'Montando o kit…' }}
                }
              </div>
              <button
                type="button"
                class="btn sm"
                (click)="generate(false)"
                [disabled]="busy()"
                [attr.aria-busy]="(busy() && !busyAi()) || null"
              >
                Gerar sem IA
              </button>
              @if (o.aiEnabled) {
                <button
                  type="button"
                  class="btn sm primary"
                  (click)="generate(true)"
                  [disabled]="busy()"
                  [attr.aria-busy]="(busy() && busyAi()) || null"
                >
                  <app-icon name="sparkles" [size]="14" />Gerar com IA
                </button>
              }
            </div>
          }

          @if (kit(); as k) {
            <section class="letters" aria-label="Textos do kit">
              @if (k.warnings.length) {
                <ul class="warnings">
                  @for (w of k.warnings; track $index) {
                    <li><app-icon name="alert" [size]="14" />{{ w }}</li>
                  }
                </ul>
              }
              <p class="total">
                Total oferecido: <strong class="num">{{ k.total | currency: 'BRL' }}</strong>
                @if (k.writtenByAi) {
                  <span class="badge ai"
                    ><app-icon name="sparkles" [size]="12" />Escrito com IA</span
                  >
                }
              </p>
              <div class="tabs" role="tablist" aria-label="Cartas do kit">
                @for (d of drafts(); track d.kind) {
                  <button
                    type="button"
                    role="tab"
                    class="tab"
                    [id]="'kit-tab-' + d.kind"
                    [attr.aria-selected]="active() === d.kind"
                    [attr.aria-controls]="'kit-panel-' + d.kind"
                    (click)="active.set(d.kind)"
                  >
                    {{ d.title }}
                  </button>
                }
              </div>
              @for (d of drafts(); track d.kind) {
                @if (active() === d.kind) {
                  <div
                    class="letter"
                    role="tabpanel"
                    [id]="'kit-panel-' + d.kind"
                    [attr.aria-labelledby]="'kit-tab-' + d.kind"
                  >
                    @if (d.kind === 'APRESENTACAO') {
                      <p class="meta">
                        <span class="meta-label">Para</span>
                        {{ to() ?? 'O município não tem contato com e-mail. Cadastre em Órgãos.' }}
                      </p>
                      <div class="field">
                        <label for="kit-subject">Assunto</label>
                        <input
                          id="kit-subject"
                          class="input"
                          name="kitSubject"
                          maxlength="200"
                          [ngModel]="d.subject"
                          (ngModelChange)="edit(d.kind, 'subject', $event)"
                        />
                      </div>
                    } @else if (d.kind === 'SOLICITACAO') {
                      <p class="hint">
                        Modelo para o município adaptar e mandar ao órgão gerenciador. Os campos
                        entre colchetes são dele.
                      </p>
                    } @else {
                      <p class="hint">
                        Texto fixo de aceite da empresa: confira os dados e as quantidades.
                      </p>
                    }
                    <div class="field">
                      <label [for]="'kit-body-' + d.kind">Texto</label>
                      <textarea
                        [id]="'kit-body-' + d.kind"
                        class="input textarea"
                        [name]="'kit-body-' + d.kind"
                        rows="14"
                        maxlength="20000"
                        [ngModel]="d.body"
                        (ngModelChange)="edit(d.kind, 'body', $event)"
                      ></textarea>
                    </div>
                    <div class="actions">
                      <span class="done" role="status">{{ done() }}</span>
                      <button type="button" class="btn sm" (click)="copy(d)">
                        <app-icon name="copy" [size]="14" />Copiar
                      </button>
                      <button
                        type="button"
                        class="btn sm"
                        (click)="download(d)"
                        [disabled]="fileBusy()"
                      >
                        <app-icon name="download" [size]="14" />Baixar .docx
                      </button>
                      @if (o().offer.canEdit) {
                        <button
                          type="button"
                          class="btn sm"
                          (click)="saveToDocuments(d)"
                          [disabled]="fileBusy()"
                          [attr.aria-busy]="fileBusy() || null"
                        >
                          <app-icon name="records" [size]="14" />Salvar nos documentos
                        </button>
                      }
                      @if (d.kind === 'APRESENTACAO') {
                        <a class="btn sm primary" [href]="mailto(d)"
                          ><app-icon name="mail" [size]="14" />Abrir no e-mail</a
                        >
                      }
                    </div>
                  </div>
                }
              }
            </section>
          }
        </div>
      }
      <ng-container panel-footer>
        <span class="footer-note">O LicitaOne não envia nada por você.</span>
        <button type="button" class="btn" (click)="closed.emit()">Fechar</button>
      </ng-container>
    </app-panel>
  `,
  styleUrl: './carona-kit-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CaronaKitDialog {
  private readonly api = inject(ExtractionsService);
  private readonly documentsApi = inject(DocumentsService);

  readonly open = input(false);
  readonly record = input<PriceRecordResponse | null>(null);
  /** Município já escolhido (ex.: "Kit de carona" numa negociação). */
  readonly presetAgency = input<AgencyRefResponse | null>(null);
  readonly closed = output<void>();
  /** Uma carta foi salva nos documentos da licitação. */
  readonly documentSaved = output<void>();

  protected readonly qty = formatQty;
  protected readonly agency = signal<AgencyRefResponse | null>(null);
  protected readonly offer = signal<CaronaOfferResponse | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly busyAi = signal(false);
  protected readonly fileBusy = signal(false);
  protected readonly kit = signal<CaronaKitTextsResponse | null>(null);
  protected readonly drafts = signal<Draft[]>([]);
  protected readonly active = signal<LetterKind>('APRESENTACAO');
  protected readonly to = signal<string | null>(null);
  protected readonly done = signal('');
  /** Para o template: a oferta existe sempre que há kit. */
  protected readonly o = computed(() => this.offer()!);

  protected quantities: Record<string, string> = {};
  protected value = '';

  constructor() {
    effect(() => {
      if (!this.open()) return;
      const preset = this.presetAgency();
      this.reset();
      this.agency.set(preset);
      if (preset) this.load(preset);
    });
  }

  protected pick(agency: AgencyRefResponse | null): void {
    this.reset();
    this.agency.set(agency);
    if (agency) this.load(agency);
  }

  protected formatValue(text: string): string {
    const parsed = parseBrl(text);
    return parsed == null ? text : formatBrlInput(parsed);
  }

  protected generate(useAi: boolean): void {
    const r = this.record();
    const agency = this.agency();
    const offer = this.offer();
    if (!r || !agency || !offer) return;
    const lines = offer.offer.items.map((i) => ({
      itemId: i.itemId,
      quantity: parseQuantity(this.quantities[i.itemId]) ?? 0,
    }));
    if (offer.offer.items.length && !lines.some((l) => l.quantity > 0)) {
      this.error.set('Informe a quantidade de pelo menos um item.');
      return;
    }
    this.error.set(null);
    this.done.set('');
    this.busy.set(true);
    this.busyAi.set(useAi);
    this.api
      .writeCaronaKit(r.id, {
        agencyId: agency.id,
        useAi,
        lines: offer.offer.items.length ? lines : undefined,
        value: offer.offer.items.length ? undefined : (parseBrl(this.value) ?? undefined),
      })
      .subscribe({
        next: (view) => {
          this.busy.set(false);
          this.kit.set(view.kit);
          this.to.set(view.to ?? null);
          this.drafts.set(
            [view.kit.presentation, view.kit.request, view.kit.consent].map((l) => this.draft(l)),
          );
          this.active.set('APRESENTACAO');
        },
        error: (err: unknown) => {
          this.busy.set(false);
          this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível gerar o kit.');
        },
      });
  }

  protected edit(kind: LetterKind, field: 'subject' | 'body', text: string): void {
    this.drafts.update((list) => list.map((d) => (d.kind === kind ? { ...d, [field]: text } : d)));
  }

  protected mailto(d: Draft): string {
    return (
      `mailto:${encodeURIComponent(this.to() ?? '').replace(/%2C/g, ',')}` +
      `?subject=${encodeURIComponent(d.subject)}&body=${encodeURIComponent(d.body)}`
    );
  }

  protected copy(d: Draft): void {
    const text = d.subject ? `${d.subject}\n\n${d.body}` : d.body;
    navigator.clipboard.writeText(text).then(
      () => this.done.set('Texto copiado.'),
      () => this.done.set('Não deu para copiar. Selecione o texto e copie.'),
    );
  }

  protected download(d: Draft): void {
    this.docx(d, (blob, name) => {
      saveBlob(blob, name);
      this.done.set('Arquivo baixado.');
    });
  }

  /** Gera o .docx e envia como documento "Outros" da licitação da ata. */
  protected saveToDocuments(d: Draft): void {
    const r = this.record();
    if (!r) return;
    this.docx(d, (blob, name) => {
      this.fileBusy.set(true);
      const title = `Kit de carona · ${d.title} · ${this.agency()?.name ?? ''}`.slice(0, 200);
      this.documentsApi
        .uploadDocument(
          { tenderId: r.tender.id, kind: 'OUTROS', title },
          { file: new File([blob], name, { type: blob.type }) },
        )
        .subscribe({
          next: () => {
            this.fileBusy.set(false);
            this.done.set('Salvo nos documentos da licitação.');
            this.documentSaved.emit();
          },
          error: (err: unknown) => this.fileFailed(err),
        });
    });
  }

  private docx(d: Draft, then: (blob: Blob, name: string) => void): void {
    const r = this.record();
    const agency = this.agency();
    if (!r || !agency) return;
    this.fileBusy.set(true);
    this.done.set('');
    this.api
      .caronaLetterDocx(
        r.id,
        { agencyId: agency.id, kind: d.kind, text: d.body },
        { observe: 'response' },
      )
      .subscribe({
        next: (response) => {
          this.fileBusy.set(false);
          const blob = response.body;
          if (!blob) return;
          then(blob, fileName(response.headers.get('Content-Disposition')) ?? `${d.title}.docx`);
        },
        error: (err: unknown) => this.fileFailed(err),
      });
  }

  private fileFailed(err: unknown): void {
    this.fileBusy.set(false);
    this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível gerar o arquivo.');
  }

  private load(agency: AgencyRefResponse): void {
    const r = this.record();
    if (!r) return;
    this.loading.set(true);
    this.api.caronaOffer(r.id, { agencyId: agency.id }).subscribe({
      next: (view) => {
        if (this.agency()?.id !== agency.id) return;
        this.loading.set(false);
        this.offer.set(view);
        this.quantities = Object.fromEntries(
          view.offer.items.map((i) => [
            i.itemId,
            i.maxQuantity > 0 ? formatQty(i.maxQuantity) : '',
          ]),
        );
        this.value = view.offer.maxValue ? formatBrlInput(view.offer.maxValue) : '';
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível conferir a ata.');
      },
    });
  }

  private reset(): void {
    this.offer.set(null);
    this.kit.set(null);
    this.drafts.set([]);
    this.error.set(null);
    this.done.set('');
    this.to.set(null);
    this.quantities = {};
    this.value = '';
  }

  private draft(letter: CaronaLetterResponse): Draft {
    return {
      kind: letter.kind as LetterKind,
      title: letter.title,
      subject: letter.subject ?? '',
      body: letter.body,
    };
  }
}

/** Nome do arquivo no Content-Disposition (prefere o filename* em UTF-8). */
export function fileName(header: string | null): string | null {
  if (!header) return null;
  const star = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (star) return decodeURIComponent(star[1]);
  const plain = /filename="([^"]+)"/i.exec(header);
  return plain ? plain[1] : null;
}
