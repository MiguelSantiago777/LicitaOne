import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subscription, timer } from 'rxjs';
import { switchMap, take, takeWhile } from 'rxjs/operators';
import { DocumentsService } from '../../../api/generated/documents/documents.service';
import { ExtractionsService } from '../../../api/generated/extractions/extractions.service';
import type { DocumentResponse, OrderReadingResponse } from '../../../api/generated/model';
import { ApiError } from '../../../core/http/api-error';
import { Icon } from '../../../shared/ui/icon';

/** Tipos de documento que trazem pedido e formatos que a IA lê (o backend confere de novo). */
const ORDER_KINDS = ['EMPENHO', 'OUTROS'];
const READABLE = ['application/pdf', 'image/png', 'image/jpeg'];
const POLL_INTERVAL_MS = 2000;
const POLL_MAX = 150;
const MAX_TEXT = 20000;

function current(d: DocumentResponse) {
  return d.versions.find((v) => v.number === d.currentVersion) ?? d.versions[0];
}

/**
 * "Preencher com IA" no diálogo de pedido (Fase 8): a IA lê um ofício, AF, empenho ou foto já enviado em Documentos,
 * ou uma mensagem colada, e relaciona cada linha com os itens do contrato. A leitura roda em segundo plano; quem
 * preenche os campos é o diálogo.
 */
@Component({
  selector: 'app-order-ai-fill',
  imports: [FormsModule, Icon],
  template: `
    @if (settings.value()?.enabled) {
      <section class="ai-fill" aria-labelledby="order-ai-title">
        <div class="ai-head">
          <app-icon name="sparkles" [size]="18" />
          <div>
            <h3 id="order-ai-title">Preencher com IA</h3>
            <p>
              A IA lê o pedido do órgão e preenche as quantidades. Confira tudo antes de registrar.
            </p>
          </div>
        </div>
        <div class="modes" role="radiogroup" aria-label="De onde vem o pedido">
          <button
            type="button"
            role="radio"
            class="mode"
            [attr.aria-checked]="mode() === 'document'"
            (click)="mode.set('document')"
            [disabled]="busy()"
          >
            <app-icon name="records" [size]="14" />Documento ou foto
          </button>
          <button
            type="button"
            role="radio"
            class="mode"
            [attr.aria-checked]="mode() === 'text'"
            (click)="mode.set('text')"
            [disabled]="busy()"
          >
            <app-icon name="mail" [size]="14" />Mensagem colada
          </button>
        </div>
        @if (mode() === 'document') {
          @if (readable().length) {
            <div class="ai-row">
              <label class="visually-hidden" for="order-ai-document">Pedido para a IA ler</label>
              <select
                id="order-ai-document"
                class="input"
                name="orderAiDocument"
                [ngModel]="documentId()"
                (ngModelChange)="documentId.set($event)"
                [disabled]="busy()"
              >
                @for (d of readable(); track d.id) {
                  <option [value]="d.id">{{ d.title }} ({{ d.kindLabel }})</option>
                }
              </select>
              <button
                type="button"
                class="btn sm primary"
                (click)="read()"
                [disabled]="busy() || !documentId()"
                [attr.aria-busy]="busy() || null"
              >
                <app-icon name="sparkles" [size]="14" />{{ busy() ? 'Lendo…' : 'Ler com IA' }}
              </button>
            </div>
          } @else {
            <p class="ai-previous">
              Envie aqui o ofício, a AF, o empenho ou a foto do pedido (PDF, JPEG ou PNG): ele fica
              guardado em Documentos e a IA lê em seguida. Ou cole a mensagem do órgão.
            </p>
          }
          <div class="ai-upload">
            <label
              class="btn sm file-btn"
              [class.primary]="!readable().length"
              [class.busy]="busy()"
            >
              <app-icon name="plus" [size]="14" />{{
                readable().length ? 'Enviar outro arquivo e ler' : 'Enviar arquivo e ler com IA'
              }}
              <input
                type="file"
                accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
                [disabled]="busy()"
                (change)="upload($event)"
                aria-label="Enviar o pedido (PDF ou foto) para a IA ler"
              />
            </label>
          </div>
        } @else {
          <div class="field">
            <label for="order-ai-text">E-mail ou mensagem do órgão</label>
            <textarea
              id="order-ai-text"
              class="input textarea"
              name="orderAiText"
              rows="4"
              [maxlength]="maxText"
              [ngModel]="text()"
              (ngModelChange)="text.set($event)"
              [disabled]="busy()"
              placeholder="Cole aqui o e-mail ou a mensagem com o pedido"
            ></textarea>
            <p class="hint">A mensagem vai para a IA e não fica guardada depois da leitura.</p>
          </div>
          <div class="ai-actions">
            <button
              type="button"
              class="btn sm primary"
              (click)="read()"
              [disabled]="busy() || text().trim().length < 10"
              [attr.aria-busy]="busy() || null"
            >
              <app-icon name="sparkles" [size]="14" />{{ busy() ? 'Lendo…' : 'Ler com IA' }}
            </button>
          </div>
        }
        <div class="ai-status" role="status" aria-live="polite">
          @if (busy()) {
            <span class="spinner" aria-hidden="true"></span>
            Lendo o pedido. Isso costuma levar menos de um minuto; você pode continuar preenchendo.
          }
        </div>
        @if (error(); as message) {
          <div class="alert" role="alert">{{ message }}</div>
        }
      </section>
    }
  `,
  styleUrl: '../../price-records/document-ai-fill.scss',
  styles: `
    .modes {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
    }
    .mode {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      height: 30px;
      padding: 0 12px;
      border: 1px solid var(--border);
      border-radius: 999px;
      background: var(--surface);
      color: var(--text-2);
      font: inherit;
      font-size: 13px;
      font-weight: 550;
      cursor: pointer;
    }
    .mode[aria-checked='true'] {
      border-color: var(--violet);
      color: var(--violet);
    }
    .mode:focus-visible {
      outline: 2px solid var(--violet);
      outline-offset: 2px;
    }
    .textarea {
      height: auto;
      padding: 10px 12px;
      resize: vertical;
      line-height: 20px;
      background: var(--surface);
    }
    .hint {
      margin-top: 4px;
      color: var(--text-3);
      font-size: 12px;
    }
    .ai-actions {
      display: flex;
      justify-content: flex-end;
    }
    .alert {
      padding: 10px 14px;
      border-radius: var(--radius-sm);
      background: var(--red-bg);
      color: var(--red);
      font-weight: 500;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderAiFill {
  private readonly api = inject(ExtractionsService);
  private readonly documentsApi = inject(DocumentsService);
  private readonly destroyRef = inject(DestroyRef);

  readonly contractId = input.required<string>();
  /** Licitação do contrato, para guardar em Documentos o arquivo enviado aqui. */
  readonly tenderId = input.required<string>();
  /** Documentos da licitação; só os de pedido (Empenho, Outros) em PDF, JPEG ou PNG entram. */
  readonly documents = input<DocumentResponse[]>([]);
  /** Texto já colado em outro lugar (registro de contato): abre em "Mensagem colada" com ele. */
  readonly presetText = input<string | null>(null);
  readonly done = output<OrderReadingResponse>();

  protected readonly maxText = MAX_TEXT;
  protected readonly settings = rxResource({ stream: () => this.api.getExtractionSettings() });
  /** Arquivos enviados por esta caixa, que a lista de documentos de quem chamou ainda não tem. */
  private readonly uploaded = signal<DocumentResponse[]>([]);
  protected readonly readable = computed(() => {
    const known = this.documents();
    const extra = this.uploaded().filter((u) => !known.some((d) => d.id === u.id));
    return [...known, ...extra].filter(
      (d) => ORDER_KINDS.includes(d.kind) && READABLE.includes(current(d)?.contentType ?? ''),
    );
  });
  protected readonly mode = signal<'document' | 'text'>('document');
  protected readonly documentId = signal('');
  protected readonly text = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  private polling?: Subscription;

  constructor() {
    effect(() => {
      const preset = this.presetText();
      if (!preset) return;
      this.mode.set('text');
      this.text.set(preset);
    });
    effect(() => {
      const docs = this.readable();
      if (docs.some((d) => d.id === this.documentId())) return;
      // O mais recente primeiro: costuma ser o pedido que acabou de chegar
      const latest = [...docs].sort((a, b) =>
        (current(b)?.uploadedAt ?? '').localeCompare(current(a)?.uploadedAt ?? ''),
      )[0];
      this.documentId.set(latest?.id ?? '');
    });
  }

  /** Envia o pedido como documento "Outros" da licitação e já pede a leitura. */
  protected upload(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    const maxMb = this.settings.value()?.maxFileMb ?? 20;
    if (!/\.(pdf|jpe?g|png)$/i.test(file.name)) {
      this.error.set('Escolha um PDF, JPEG ou PNG.');
      return;
    }
    if (file.size > maxMb * 1024 * 1024) {
      this.error.set(`O arquivo passa de ${maxMb} MB, o máximo que a IA lê de uma vez.`);
      return;
    }
    this.error.set(null);
    this.busy.set(true);
    const title = `Pedido · ${file.name.replace(/\.[^.]+$/, '')}`.slice(0, 200);
    this.documentsApi
      .uploadDocument({ tenderId: this.tenderId(), kind: 'OUTROS', title }, { file })
      .subscribe({
        next: (doc) => {
          this.uploaded.update((list) => [...list, doc]);
          this.documentId.set(doc.id);
          this.busy.set(false);
          this.read();
        },
        error: (err: unknown) => this.fail(err),
      });
  }

  protected read(): void {
    const body =
      this.mode() === 'document' ? { documentId: this.documentId() } : { text: this.text().trim() };
    if (!body.documentId && !body.text) return;
    this.error.set(null);
    this.busy.set(true);
    this.api.requestOrderReading(this.contractId(), body).subscribe({
      next: (r) => this.follow(r.id),
      error: (err: unknown) => this.fail(err),
    });
  }

  private follow(readingId: string): void {
    this.polling?.unsubscribe();
    this.polling = timer(POLL_INTERVAL_MS, POLL_INTERVAL_MS)
      .pipe(
        take(POLL_MAX),
        switchMap(() => this.api.getOrderReading(readingId)),
        takeWhile((r) => r.status === 'QUEUED' || r.status === 'RUNNING', true),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (r) => {
          if (r.status === 'DONE') {
            this.busy.set(false);
            if (r.source === 'TEXT') this.text.set('');
            this.done.emit(r);
          } else if (r.status === 'FAILED') {
            this.busy.set(false);
            this.error.set(r.errorMessage ?? 'A IA não conseguiu ler o pedido.');
          }
        },
        error: (err: unknown) => this.fail(err),
        complete: () => {
          if (this.busy()) {
            this.busy.set(false);
            this.error.set(
              'A leitura está demorando mais que o normal. Tente de novo em alguns minutos.',
            );
          }
        },
      });
  }

  private fail(err: unknown): void {
    this.busy.set(false);
    this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível pedir a leitura.');
  }
}
