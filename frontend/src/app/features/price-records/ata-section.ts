import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
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
import { RouterLink } from '@angular/router';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { DocumentsService } from '../../api/generated/documents/documents.service';
import type {
  CaronaResponse,
  PriceRecordResponse,
  TenderResponse,
} from '../../api/generated/model';
import { PriceRecordsService } from '../../api/generated/price-records/price-records.service';
import { ApiError } from '../../core/http/api-error';
import { Icon } from '../../shared/ui/icon';
import { Panel } from '../../shared/ui/panel';
import { AtaItemsDialog } from './ata-items-dialog';
import { CaronaDialog, CaronaPreset } from './carona-dialog';
import { saveBlob } from './documents-section';
import { formatQty, situationLabel, situationTone } from './price-record-meta';

/** Estados em que a licitação já foi vencida: só elas têm ata. */
const WON = ['VENCIDA', 'AGUARDANDO_ASSINATURA', 'CONTRATO_ASSINADO', 'EM_ANDAMENTO', 'ENCERRADO'];

/**
 * A ata da licitação: vigência (independente do encerramento do contrato), itens registrados e caronas. Mostra o
 * quanto já foi usado do limite configurado e registra caronas.
 */
@Component({
  selector: 'app-ata-section',
  imports: [
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    FormsModule,
    RouterLink,
    Icon,
    Panel,
    AtaItemsDialog,
    CaronaDialog,
  ],
  templateUrl: './ata-section.html',
  styleUrl: './ata-section.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AtaSection {
  private readonly api = inject(PriceRecordsService);
  private readonly documentsApi = inject(DocumentsService);

  readonly tender = input.required<TenderResponse>();
  /** "Registrar carona" vindo de uma negociação ganha: abre o diálogo já preenchido. */
  readonly caronaPreset = input<CaronaPreset | null>(null);
  /** Ata criada, alterada ou apagada (o cabeçalho e o histórico recarregam). */
  readonly changed = output<void>();

  protected readonly qty = formatQty;
  protected readonly situationLabel = situationLabel;
  protected readonly tone = situationTone;

  /** 404 quer dizer "ainda não tem ata", não erro. */
  protected readonly ata = rxResource({
    params: () => this.tender().id,
    stream: ({ params }) =>
      this.api.getPriceRecordByTender(params).pipe(
        catchError((err: unknown) => {
          if (err instanceof ApiError && err.code === 'NOT_FOUND') return of(null);
          throw err;
        }),
      ),
  });
  protected readonly canCreate = computed(
    () => this.tender().canEdit && WON.includes(this.tender().status),
  );
  /** Documentos do tipo Ata, para ligar o arquivo à ata. */
  protected readonly ataDocuments = rxResource({
    params: () => (this.formOpen() ? this.tender().id : undefined),
    stream: ({ params }) => this.documentsApi.listDocuments({ tenderId: params }),
  });

  protected readonly formOpen = signal(false);
  protected readonly itemsOpen = signal(false);
  protected readonly caronaOpen = signal(false);
  protected readonly activePreset = signal<CaronaPreset | null>(null);
  protected readonly confirmingCarona = signal<string | null>(null);
  protected readonly confirmingDelete = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly formError = signal<string | null>(null);

  protected number = '';
  protected signedAt = '';
  protected validUntil = '';
  protected allowsAdhesion = true;
  protected notes = '';
  protected documentId = '';

  constructor() {
    // Chegou de uma negociação ganha: abre a carona assim que a ata carregar
    effect(() => {
      const preset = this.caronaPreset();
      const ata = this.ata.value();
      if (preset && ata && ata.allowsAdhesion && ata.canEdit && !this.caronaOpen()) {
        this.openCarona(preset);
      }
    });
  }

  protected openForm(ata: PriceRecordResponse | null): void {
    this.number = ata?.number ?? '';
    this.signedAt = ata?.signedAt ?? '';
    this.validUntil = ata?.validUntil ?? '';
    this.allowsAdhesion = ata?.allowsAdhesion ?? true;
    this.notes = ata?.notes ?? '';
    this.documentId = ata?.document?.id ?? '';
    this.formError.set(null);
    this.formOpen.set(true);
  }

  protected saveForm(ata: PriceRecordResponse | null): void {
    if (!this.number.trim()) {
      this.formError.set('Informe o número da ata.');
      return;
    }
    if (!this.validUntil) {
      this.formError.set('Informe até quando a ata vale.');
      return;
    }
    const record = {
      number: this.number.trim(),
      signedAt: this.signedAt || undefined,
      validUntil: this.validUntil,
      allowsAdhesion: this.allowsAdhesion,
      notes: this.notes.trim() || undefined,
      documentId: this.documentId || undefined,
    };
    this.saving.set(true);
    this.formError.set(null);
    const request = ata
      ? this.api.updatePriceRecord(ata.id, { version: ata.version, record })
      : this.api.createPriceRecord({ tenderId: this.tender().id, record });
    request.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.accept(saved);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.formError.set(err instanceof ApiError ? err.detail : 'Não foi possível salvar.');
      },
    });
  }

  protected openCarona(preset: CaronaPreset | null = null): void {
    this.activePreset.set(preset);
    this.caronaOpen.set(true);
  }

  protected removeCarona(ata: PriceRecordResponse, carona: CaronaResponse): void {
    this.api.removeCarona(ata.id, carona.id, { version: ata.version }).subscribe({
      next: (updated) => {
        this.confirmingCarona.set(null);
        this.accept(updated);
      },
      error: (err: unknown) => this.fail(err),
    });
  }

  protected deleteAta(ata: PriceRecordResponse): void {
    this.api.deletePriceRecord(ata.id, { version: ata.version }).subscribe({
      next: () => {
        this.confirmingDelete.set(false);
        this.ata.set(null);
        this.changed.emit();
      },
      error: (err: unknown) => {
        this.confirmingDelete.set(false);
        this.fail(err);
      },
    });
  }

  protected downloadDocument(ata: PriceRecordResponse): void {
    const doc = ata.document;
    if (!doc) return;
    this.documentsApi.downloadDocument(doc.id, doc.currentVersion).subscribe({
      next: (blob) => saveBlob(blob, doc.fileName),
      error: (err: unknown) => this.fail(err),
    });
  }

  protected accept(updated: PriceRecordResponse): void {
    this.itemsOpen.set(false);
    this.caronaOpen.set(false);
    this.activePreset.set(null);
    this.error.set(null);
    this.ata.set(updated);
    this.changed.emit();
  }

  protected usedPercentBar(ata: PriceRecordResponse): number {
    return Math.min(100, (ata.usedPercent / ata.limits.maxTotalPercent) * 100);
  }

  protected itemBar(used: number, limit: number): number {
    return limit > 0 ? Math.min(100, (used / limit) * 100) : 0;
  }

  protected linesOf(c: CaronaResponse): string {
    return c.lines.map((l) => `${formatQty(l.quantity)} × ${l.description ?? 'item'}`).join('; ');
  }

  private fail(err: unknown): void {
    this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível concluir.');
    if (err instanceof ApiError && err.code === 'VERSION_CONFLICT') this.ata.reload();
  }
}
