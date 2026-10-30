import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { DocumentsService } from '../../api/generated/documents/documents.service';
import type {
  DocumentResponse,
  DocumentResponseKind,
  DocumentVersionResponse,
} from '../../api/generated/model';
import { ApiError } from '../../core/http/api-error';
import { Icon } from '../../shared/ui/icon';
import { Panel } from '../../shared/ui/panel';

export const DOCUMENT_KINDS: { value: DocumentResponseKind; label: string }[] = [
  { value: 'ATA', label: 'Ata' },
  { value: 'CONTRATO', label: 'Contrato' },
  { value: 'EMPENHO', label: 'Empenho' },
  { value: 'ATESTADO', label: 'Atestado' },
  { value: 'EDITAL', label: 'Edital' },
  { value: 'OUTROS', label: 'Outros' },
];

/** Os mesmos tipos que o servidor aceita (ele confere pelo conteúdo; aqui é só para o seletor de arquivos). */
const ACCEPT = '.pdf,.png,.jpg,.jpeg,.docx,.xlsx,.odt,.ods,.doc,.xls';
const MAX_BYTES = 25 * 1024 * 1024;

export function fileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} MB`;
}

/** Baixa um Blob com o nome do arquivo, sem abrir aba nova. */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  a.click();
  URL.revokeObjectURL(url);
}

/**
 * Documentos da licitação: enviar (tipo, título, data e arquivo), versão nova, baixar qualquer versão, renomear e
 * apagar. Apagar é lógico no servidor: o documento some da lista, mas os arquivos continuam guardados.
 */
@Component({
  selector: 'app-documents-section',
  imports: [DatePipe, FormsModule, Icon, Panel],
  template: `
    <section class="card">
      <header class="card-head">
        <h2>Documentos</h2>
        @if (canEdit()) {
          <button type="button" class="btn sm" (click)="openUpload()">
            <app-icon name="plus" [size]="16" />Enviar documento
          </button>
        }
      </header>
      @if (error(); as message) {
        <div class="alert" role="alert">
          {{ message }}
          <button
            type="button"
            class="btn ghost icon"
            (click)="error.set(null)"
            aria-label="Fechar aviso"
          >
            <app-icon name="x" [size]="16" />
          </button>
        </div>
      }
      <ul class="docs">
        @for (d of docs.value() ?? []; track d.id) {
          <li>
            <span class="kind" [attr.data-kind]="d.kind">{{ d.kindLabel }}</span>
            <div class="doc-main">
              <span class="cell-main">{{ d.title }}</span>
              <span class="cell-sub num">
                @if (d.documentDate) {
                  {{ d.documentDate | date: 'dd/MM/yyyy' }} ·
                }
                {{ latest(d).fileName }} · {{ size(latest(d).sizeBytes) }} · versão
                {{ d.currentVersion }}
              </span>
            </div>
            <button
              type="button"
              class="btn sm"
              (click)="download(d, latest(d))"
              [attr.aria-label]="'Baixar ' + d.title"
            >
              <app-icon name="download" [size]="16" />Baixar
            </button>
            <button
              type="button"
              class="btn ghost sm"
              [attr.aria-expanded]="expanded() === d.id"
              (click)="expanded.set(expanded() === d.id ? null : d.id)"
            >
              {{ d.versions.length === 1 ? 'Detalhes' : d.versions.length + ' versões' }}
            </button>
            @if (expanded() === d.id) {
              <div class="versions">
                <ol>
                  @for (v of d.versions; track v.number) {
                    <li>
                      <span class="num">v{{ v.number }}</span>
                      <span class="grow">{{ v.fileName }}</span>
                      <span class="cell-sub num"
                        >{{ v.uploadedBy ?? 'Sistema' }} ·
                        {{ v.uploadedAt | date: "dd/MM/yyyy 'às' HH:mm" }}</span
                      >
                      <button
                        type="button"
                        class="btn ghost sm"
                        (click)="download(d, v)"
                        [attr.aria-label]="'Baixar versão ' + v.number + ' de ' + d.title"
                      >
                        <app-icon name="download" [size]="14" />
                      </button>
                    </li>
                  }
                </ol>
                @if (d.canEdit) {
                  <div class="doc-actions">
                    <label class="btn sm file-btn">
                      <app-icon name="plus" [size]="14" />Versão nova
                      <input
                        type="file"
                        [accept]="accept"
                        (change)="addVersion(d, $event)"
                        [attr.aria-label]="'Enviar versão nova de ' + d.title"
                      />
                    </label>
                    <button type="button" class="btn ghost sm" (click)="openEdit(d)">
                      Renomear
                    </button>
                    @if (confirmingDelete() === d.id) {
                      <span class="confirm">Apagar este documento?</span>
                      <button type="button" class="btn sm danger" (click)="remove(d)">
                        Apagar
                      </button>
                      <button type="button" class="btn sm" (click)="confirmingDelete.set(null)">
                        Manter
                      </button>
                    } @else {
                      <button
                        type="button"
                        class="btn ghost sm danger-text"
                        (click)="confirmingDelete.set(d.id)"
                      >
                        Apagar
                      </button>
                    }
                  </div>
                }
              </div>
            }
          </li>
        } @empty {
          <li class="empty">
            @if (docs.isLoading()) {
              Carregando…
            } @else {
              Nenhum documento. Envie a ata, o contrato, os empenhos e os atestados desta licitação.
            }
          </li>
        }
      </ul>
    </section>

    <app-panel
      [open]="formOpen()"
      [title]="editingDoc() ? 'Renomear documento' : 'Enviar documento'"
      [width]="480"
      (closed)="formOpen.set(false)"
    >
      @if (formOpen()) {
        <form id="doc-form" class="form" (ngSubmit)="submit()" novalidate>
          @if (formError(); as message) {
            <div class="alert" role="alert">{{ message }}</div>
          }
          <div class="field">
            <label for="doc-kind">Tipo</label>
            <select id="doc-kind" class="input" name="kind" [(ngModel)]="kind">
              @for (k of kinds; track k.value) {
                <option [value]="k.value">{{ k.label }}</option>
              }
            </select>
          </div>
          <div class="field">
            <label for="doc-title">Título</label>
            <input
              id="doc-title"
              class="input"
              name="title"
              [(ngModel)]="title"
              maxlength="200"
              placeholder="Ex.: Ata de registro de preços 017/2026"
              autocomplete="off"
            />
          </div>
          <div class="field">
            <label for="doc-date">Data do documento</label>
            <input
              id="doc-date"
              type="date"
              class="input"
              name="documentDate"
              [(ngModel)]="documentDate"
            />
          </div>
          @if (!editingDoc()) {
            <div class="field">
              <label for="doc-file">Arquivo</label>
              <input
                #fileInput
                id="doc-file"
                type="file"
                class="input file"
                [accept]="accept"
                (change)="pick($event)"
              />
              <p class="hint">PDF, imagem, Word, Excel ou ODF, até 25 MB.</p>
            </div>
          }
        </form>
      }
      <ng-container panel-footer>
        <button type="button" class="btn" (click)="formOpen.set(false)">Cancelar</button>
        <button
          type="submit"
          form="doc-form"
          class="btn primary"
          [disabled]="busy()"
          [attr.aria-busy]="busy() || null"
        >
          {{ busy() ? 'Enviando…' : editingDoc() ? 'Salvar' : 'Enviar' }}
        </button>
      </ng-container>
    </app-panel>
  `,
  styleUrl: './documents-section.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DocumentsSection {
  private readonly api = inject(DocumentsService);

  readonly tenderId = input.required<string>();
  readonly canEdit = input(false);

  protected readonly kinds = DOCUMENT_KINDS;
  protected readonly accept = ACCEPT;
  protected readonly size = fileSize;
  protected readonly docs = rxResource({
    params: () => this.tenderId(),
    stream: ({ params }) => this.api.listDocuments({ tenderId: params }),
  });
  protected readonly expanded = signal<string | null>(null);
  protected readonly confirmingDelete = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly formError = signal<string | null>(null);
  protected readonly formOpen = signal(false);
  protected readonly editingDoc = signal<DocumentResponse | null>(null);
  protected readonly busy = signal(false);
  private readonly fileInput = viewChild<ElementRef<HTMLInputElement>>('fileInput');

  protected kind: DocumentResponseKind = 'ATA';
  protected title = '';
  protected documentDate = '';
  private file: File | null = null;

  protected latest(d: DocumentResponse): DocumentVersionResponse {
    return d.versions[0];
  }

  protected openUpload(): void {
    this.editingDoc.set(null);
    this.kind = 'ATA';
    this.title = '';
    this.documentDate = '';
    this.file = null;
    this.formError.set(null);
    this.formOpen.set(true);
  }

  protected openEdit(d: DocumentResponse): void {
    this.editingDoc.set(d);
    this.kind = d.kind;
    this.title = d.title;
    this.documentDate = d.documentDate ?? '';
    this.formError.set(null);
    this.formOpen.set(true);
  }

  protected pick(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.file = input.files?.[0] ?? null;
    if (this.file && !this.title.trim()) {
      // O nome do arquivo sem extensão é um bom ponto de partida para o título
      this.title = this.file.name.replace(/\.[^.]+$/, '');
    }
  }

  protected submit(): void {
    if (!this.title.trim()) {
      this.formError.set('Informe o título do documento.');
      return;
    }
    const editing = this.editingDoc();
    if (editing) {
      this.busy.set(true);
      this.api
        .describeDocument(editing.id, {
          version: editing.version,
          kind: this.kind,
          title: this.title.trim(),
          documentDate: this.documentDate || undefined,
        })
        .subscribe({
          next: () => this.done(),
          error: (err: unknown) => this.failForm(err),
        });
      return;
    }
    if (!this.file) {
      this.formError.set('Escolha o arquivo.');
      return;
    }
    if (this.file.size > MAX_BYTES) {
      this.formError.set('O arquivo passa de 25 MB.');
      return;
    }
    this.busy.set(true);
    this.api
      .uploadDocument(
        {
          tenderId: this.tenderId(),
          kind: this.kind,
          title: this.title.trim(),
          documentDate: this.documentDate || undefined,
        },
        { file: this.file },
      )
      .subscribe({
        next: () => this.done(),
        error: (err: unknown) => this.failForm(err),
      });
  }

  protected addVersion(d: DocumentResponse, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (file.size > MAX_BYTES) {
      this.error.set('O arquivo passa de 25 MB.');
      return;
    }
    this.error.set(null);
    this.api.addDocumentVersion(d.id, { version: d.version }, { file }).subscribe({
      next: () => this.reload(),
      error: (err: unknown) => this.fail(err),
    });
  }

  protected download(d: DocumentResponse, v: DocumentVersionResponse): void {
    this.api.downloadDocument(d.id, v.number).subscribe({
      next: (blob) => saveBlob(blob, v.fileName),
      error: (err: unknown) => this.fail(err),
    });
  }

  protected remove(d: DocumentResponse): void {
    this.api.deleteDocument(d.id, { version: d.version }).subscribe({
      next: () => {
        this.confirmingDelete.set(null);
        this.reload();
      },
      error: (err: unknown) => {
        this.confirmingDelete.set(null);
        this.fail(err);
      },
    });
  }

  private done(): void {
    this.busy.set(false);
    this.formOpen.set(false);
    const input = this.fileInput();
    if (input) input.nativeElement.value = '';
    this.reload();
  }

  private reload(): void {
    this.docs.reload();
  }

  private failForm(err: unknown): void {
    this.busy.set(false);
    this.formError.set(err instanceof ApiError ? err.detail : 'Não foi possível enviar.');
  }

  private fail(err: unknown): void {
    this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível concluir.');
    if (err instanceof ApiError && err.code === 'VERSION_CONFLICT') this.docs.reload();
  }
}
