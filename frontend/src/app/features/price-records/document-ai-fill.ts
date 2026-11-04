import { DatePipe } from '@angular/common';
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
import { ExtractionsService } from '../../api/generated/extractions/extractions.service';
import type { DocumentResponse, ExtractionResponse } from '../../api/generated/model';
import { ApiError } from '../../core/http/api-error';
import { Icon } from '../../shared/ui/icon';

/** A leitura terminou e os campos podem ser preenchidos com ela. */
export interface AiReading {
  extraction: ExtractionResponse;
  documentId: string;
}

/** Até quanto tempo a tela acompanha uma leitura (a do modelo costuma levar menos de um minuto). */
const POLL_INTERVAL_MS = 2000;
const POLL_MAX = 150;

function isPdf(d: DocumentResponse): boolean {
  const current = d.versions.find((v) => v.number === d.currentVersion) ?? d.versions[0];
  return current?.contentType === 'application/pdf';
}

/**
 * "Preencher com IA" no formulário da ata (ADR 0016): escolhe o PDF da ata, pede a leitura (processada em segundo
 * plano) e acompanha até terminar. Se a versão atual do documento já foi lida, oferece usar a leitura anterior em
 * vez de mandar o PDF de novo. Quem preenche os campos é o formulário; aqui só a leitura.
 */
@Component({
  selector: 'app-ata-ai-fill',
  imports: [DatePipe, FormsModule, Icon],
  template: `
    @if (settings.value()?.enabled && pdfs().length) {
      <section class="ai-fill" aria-labelledby="ai-fill-title">
        <div class="ai-head">
          <app-icon name="sparkles" [size]="18" />
          <div>
            <h3 id="ai-fill-title">Preencher com IA</h3>
            <p>A IA lê o PDF da ata e preenche os campos. Confira tudo antes de salvar.</p>
          </div>
        </div>
        <div class="ai-row">
          <label class="visually-hidden" for="ai-document">PDF da ata para a IA ler</label>
          <select
            id="ai-document"
            class="input"
            name="aiDocument"
            [ngModel]="documentId()"
            (ngModelChange)="documentId.set($event)"
            [disabled]="busy()"
          >
            @for (d of pdfs(); track d.id) {
              <option [value]="d.id">{{ d.title }} (versão {{ d.currentVersion }})</option>
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
        @if (previous(); as p) {
          <p class="ai-previous">
            Este PDF já foi lido em {{ p.finishedAt | date: "dd/MM/yyyy 'às' HH:mm" }} por
            {{ p.createdByName }}.
            <button type="button" class="link inline" (click)="use(p)">Usar essa leitura</button>
          </p>
        }
        <div class="ai-status" role="status" aria-live="polite">
          @if (busy()) {
            <span class="spinner" aria-hidden="true"></span>
            Lendo o documento. Isso costuma levar menos de um minuto; você pode continuar
            preenchendo.
          }
        </div>
        @if (error(); as message) {
          <div class="alert" role="alert">{{ message }}</div>
        }
      </section>
    }
  `,
  styleUrl: './ata-ai-fill.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AtaAiFill {
  private readonly api = inject(ExtractionsService);
  private readonly destroyRef = inject(DestroyRef);

  /** Documentos da licitação; só os PDFs do tipo Ata entram. */
  readonly documents = input<DocumentResponse[]>([]);
  /** Documento já ligado à ata, para vir escolhido. */
  readonly preferredId = input<string>('');
  readonly done = output<AiReading>();

  protected readonly settings = rxResource({ stream: () => this.api.getExtractionSettings() });
  protected readonly pdfs = computed(() =>
    this.documents().filter((d) => d.kind === 'ATA' && isPdf(d)),
  );
  protected readonly documentId = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Leitura já aplicada no formulário: não faz sentido oferecer de novo. */
  private readonly applied = signal<string | null>(null);

  /** Leitura anterior concluída da versão atual do documento escolhido. */
  private readonly latest = rxResource({
    params: () => (this.settings.value()?.enabled ? this.documentId() : '') || undefined,
    stream: ({ params }) => this.api.getLatestExtraction(params),
  });
  protected readonly previous = computed(() => {
    const e = this.latest.value();
    const doc = this.pdfs().find((d) => d.id === this.documentId());
    return e &&
      doc &&
      e.status === 'DONE' &&
      e.documentVersion === doc.currentVersion &&
      e.id !== this.applied() &&
      !this.busy()
      ? e
      : null;
  });

  private polling?: Subscription;

  constructor() {
    effect(() => {
      const pdfs = this.pdfs();
      if (pdfs.some((d) => d.id === this.documentId())) return;
      const preferred = pdfs.find((d) => d.id === this.preferredId());
      this.documentId.set((preferred ?? pdfs[0])?.id ?? '');
    });
  }

  protected read(): void {
    const documentId = this.documentId();
    if (!documentId) return;
    this.error.set(null);
    this.busy.set(true);
    this.api.requestExtraction(documentId).subscribe({
      next: (e) => this.follow(e, documentId),
      error: (err: unknown) => this.fail(err),
    });
  }

  protected use(extraction: ExtractionResponse): void {
    this.applied.set(extraction.id);
    this.done.emit({ extraction, documentId: extraction.documentId });
  }

  private follow(started: ExtractionResponse, documentId: string): void {
    this.polling?.unsubscribe();
    this.polling = timer(POLL_INTERVAL_MS, POLL_INTERVAL_MS)
      .pipe(
        take(POLL_MAX),
        switchMap(() => this.api.getExtraction(started.id)),
        takeWhile((e) => e.status === 'QUEUED' || e.status === 'RUNNING', true),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (e) => {
          if (e.status === 'DONE') {
            this.busy.set(false);
            this.latest.set(e);
            this.applied.set(e.id);
            this.done.emit({ extraction: e, documentId });
          } else if (e.status === 'FAILED') {
            this.busy.set(false);
            this.error.set(e.errorMessage ?? 'A IA não conseguiu ler o documento.');
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
