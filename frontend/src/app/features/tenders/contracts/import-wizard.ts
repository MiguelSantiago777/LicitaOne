import { CurrencyPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription, filter, switchMap, take, timer } from 'rxjs';
import { ImportsService } from '../../../api/generated/imports/imports.service';
import type {
  ContractResponse,
  ImportJobResponse,
  PreviewResponse,
  PreviewRowResponse,
  SheetResponse,
  UploadResponse,
} from '../../../api/generated/model';
import { ApiError } from '../../../core/http/api-error';
import { formatQuantity, todayIso } from '../../../shared/format';
import { Icon } from '../../../shared/ui/icon';
import { Panel } from '../../../shared/ui/panel';

type Step = 'file' | 'columns' | 'preview' | 'running' | 'done';

const STATUS: Record<string, { label: string; tone: string }> = {
  CREATED: { label: 'Item novo', tone: 'green' },
  UPDATED: { label: 'Alterado', tone: 'blue' },
  UNCHANGED: { label: 'Sem mudança', tone: '' },
  WARNING: { label: 'Aviso', tone: 'amber' },
  ERROR: { label: 'Erro', tone: 'red' },
};

/**
 * Importa itens e pedidos de um contrato a partir da planilha que a equipe já usa: arquivo → colunas → prévia
 * linha a linha → processamento em segundo plano → resultado com relatório.
 */
@Component({
  selector: 'app-import-wizard',
  imports: [CurrencyPipe, FormsModule, Icon, Panel],
  templateUrl: './import-wizard.html',
  styleUrls: ['./contract-forms.scss', './import-wizard.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ImportWizard {
  private readonly api = inject(ImportsService);
  private readonly destroyRef = inject(DestroyRef);

  readonly contract = input<ContractResponse | null>(null);
  readonly agencyName = input<string | null>(null);
  readonly closed = output<void>();
  /** A importação terminou (com sucesso): o contrato mudou. */
  readonly imported = output<void>();

  protected readonly status = STATUS;
  protected readonly today = todayIso();
  protected readonly fq = formatQuantity;
  protected readonly step = signal<Step>('file');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly upload = signal<UploadResponse | null>(null);
  protected readonly sheetName = signal<string | null>(null);
  protected readonly headerRow = signal<number | null>(null);
  protected readonly mapping = signal<Record<string, number | null>>({});
  protected readonly preview = signal<PreviewResponse | null>(null);
  protected readonly job = signal<ImportJobResponse | null>(null);
  protected readonly onlyProblems = signal(false);
  protected orderDate = todayIso();

  private file: File | null = null;
  private polling?: Subscription;
  private confirmKey = '';

  protected readonly sheet = computed<SheetResponse | null>(
    () => this.upload()?.sheets.find((s) => s.name === this.sheetName()) ?? null,
  );

  protected readonly rows = computed<PreviewRowResponse[]>(() => {
    const rows = this.preview()?.rows ?? [];
    return this.onlyProblems()
      ? rows.filter((r) => r.status === 'ERROR' || r.status === 'WARNING')
      : rows;
  });

  protected readonly importable = computed(() => {
    const t = this.preview()?.totals as Record<string, number> | undefined;
    if (!t) return 0;
    return (t['CREATED'] ?? 0) + (t['UPDATED'] ?? 0) + (t['UNCHANGED'] ?? 0) + (t['WARNING'] ?? 0);
  });

  constructor() {
    effect(() => {
      if (this.contract()) this.reset();
    });
    this.destroyRef.onDestroy(() => this.polling?.unsubscribe());
  }

  protected close(): void {
    this.polling?.unsubscribe();
    this.closed.emit();
  }

  protected pick(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.file = input.files?.[0] ?? null;
    this.error.set(null);
  }

  protected send(): void {
    const c = this.contract();
    if (!c || !this.file) {
      this.error.set('Escolha a planilha (.xlsx ou .csv).');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.uploadImport({ contractId: c.id }, { file: this.file }).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.upload.set(result);
        this.selectSheet(result.suggestedSheet ?? result.sheets[0]?.name ?? null);
        this.step.set('columns');
      },
      error: (err: unknown) => this.fail(err),
    });
  }

  protected selectSheet(name: string | null): void {
    this.sheetName.set(name);
    const sheet = this.upload()?.sheets.find((s) => s.name === name);
    this.headerRow.set(sheet?.headerRow ?? 1);
    const suggested = (sheet?.suggestedMapping ?? {}) as Record<string, number>;
    const mapping: Record<string, number | null> = {};
    for (const f of this.upload()?.fields ?? []) mapping[f.field] = suggested[f.field] ?? null;
    this.mapping.set(mapping);
  }

  protected setColumn(field: string, value: string): void {
    this.mapping.update((m) => ({ ...m, [field]: value === '' ? null : Number(value) }));
  }

  protected runPreview(): void {
    const job = this.upload()?.job;
    const sheet = this.sheetName();
    const headerRow = this.headerRow();
    if (!job || !sheet || !headerRow) return;
    const mapping: Record<string, number> = {};
    for (const [field, column] of Object.entries(this.mapping())) {
      if (column != null) mapping[field] = column;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.previewImport(job.id, { sheet, headerRow, mapping }).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.preview.set(result);
        this.onlyProblems.set(false);
        this.step.set('preview');
      },
      error: (err: unknown) => this.fail(err),
    });
  }

  protected confirm(): void {
    const job = this.upload()?.job;
    if (!job) return;
    if (!this.orderDate) {
      this.error.set('Informe a data do pedido importado.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api
      .confirmImport(
        job.id,
        { orderDate: this.orderDate },
        { headers: { 'Idempotency-Key': this.confirmKey } },
      )
      .subscribe({
        next: (queued) => {
          this.busy.set(false);
          this.job.set(queued);
          this.step.set('running');
          this.poll(queued.id);
        },
        error: (err: unknown) => this.fail(err),
      });
  }

  /** O processamento é assíncrono (fila): consulta o status a cada segundo até terminar. */
  private poll(jobId: string): void {
    this.polling?.unsubscribe();
    this.polling = timer(500, 1000)
      .pipe(
        switchMap(() => this.api.getImport(jobId)),
        filter((j) => j.status === 'DONE' || j.status === 'FAILED'),
        take(1),
      )
      .subscribe({
        next: (finished) => {
          this.job.set(finished);
          this.step.set('done');
          if (finished.status === 'DONE') this.imported.emit();
        },
        error: (err: unknown) => this.fail(err),
      });
  }

  protected downloadReport(): void {
    const job = this.job();
    if (!job) return;
    this.api.importReport(job.id).subscribe({
      next: (csv) => {
        // O navegador tira o BOM ao ler o texto; sem ele o Excel estraga os acentos
        const blob = new Blob(['﻿', csv], { type: 'text/csv;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `relatorio-importacao-${job.id.slice(0, 8)}.csv`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err: unknown) => this.fail(err),
    });
  }

  protected back(): void {
    this.error.set(null);
    this.step.set(this.step() === 'preview' ? 'columns' : 'file');
  }

  protected statusOf(status: string): { label: string; tone: string } {
    return STATUS[status] ?? { label: status, tone: '' };
  }

  protected total(key: string): number {
    return ((this.preview()?.totals as Record<string, number> | undefined) ?? {})[key] ?? 0;
  }

  private fail(err: unknown): void {
    this.busy.set(false);
    this.error.set(err instanceof ApiError ? err.detail : 'Algo deu errado. Tente de novo.');
  }

  private reset(): void {
    this.polling?.unsubscribe();
    this.step.set('file');
    this.busy.set(false);
    this.error.set(null);
    this.upload.set(null);
    this.preview.set(null);
    this.job.set(null);
    this.file = null;
    this.orderDate = todayIso();
    this.confirmKey = crypto.randomUUID();
  }
}
