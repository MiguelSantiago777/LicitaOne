import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CatalogService } from '../../api/generated/catalog/catalog.service';
import type {
  ProductFileResponse,
  ProductRequest,
  ProductResponse,
} from '../../api/generated/model';
import { ApiError } from '../../core/http/api-error';
import { formatQuantity, parseQuantity, todayIso } from '../../shared/format';
import { Icon } from '../../shared/ui/icon';
import { saveBlob } from '../price-records/documents-section';
import { formatUnitPrice, parseUnitPrice } from '../proposals/proposal-format';
import { Autosave } from './autosave';
import { formatSize, parseDays, STALE_AFTER_DAYS } from './catalog-format';
import { SaveStatus } from './save-status';

/** Um preço como a pessoa digita: números ficam em texto até salvar. */
interface OfferRow {
  key: number;
  supplierId: string;
  unitPrice: string;
  quotedOn: string;
  leadTimeDays: string;
  minimumOrder: string;
}

interface Form {
  name: string;
  description: string;
  nicheId: string;
  unit: string;
  brand: string;
  catalogCode: string;
}

type FileKind = 'FOTO' | 'FICHA';

/**
 * Edição do produto: dados, preço de custo de cada fornecedor (o menor fica marcado) e arquivos (foto e ficha
 * técnica). Salva sozinha. Se o preço muda e a data não, o servidor põe a data de hoje, e a tela mostra isso.
 */
@Component({
  selector: 'app-product-page',
  imports: [DatePipe, FormsModule, RouterLink, Icon, SaveStatus],
  templateUrl: './product.page.html',
  styleUrl: './catalog-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductPage {
  private readonly api = inject(CatalogService);
  private readonly route = inject(ActivatedRoute);

  protected readonly formatSize = formatSize;
  protected readonly today = todayIso();
  private id = '';

  protected readonly product = signal<ProductResponse | null>(null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly niches = rxResource({
    stream: () => this.api.listNiches({ includeArchived: true }),
  });
  protected readonly supplierOptions = rxResource({ stream: () => this.api.supplierOptions() });

  protected form: Form = emptyForm();
  protected rows: OfferRow[] = [];
  private nextKey = 1;
  /** Muda a cada tecla: o que a tela calcula dos campos depende dele, já que o formulário é um objeto comum. */
  private readonly revision = signal(0);
  /** A data de cada fornecedor como foi no último envio, para saber se a pessoa mexeu nela depois. */
  private sentDates = new Map<string, string>();

  protected readonly saver = new Autosave<ProductResponse>(
    () => this.product(),
    (version) => this.api.updateProduct(this.id, { version, product: this.request() }),
    (p) => this.saved(p),
  );

  protected readonly busy = signal(false);
  protected readonly actionError = signal<string | null>(null);
  protected uploadKind: FileKind = 'FOTO';
  /** Endereços locais das fotos já baixadas, para mostrar a miniatura. */
  protected readonly thumbs = signal<Record<string, string>>({});

  protected readonly editable = computed(
    () => (this.product()?.canEdit ?? false) && !this.product()?.archivedAt,
  );

  protected readonly nicheChoices = computed(() =>
    (this.niches.value() ?? []).filter((n) => !n.archivedAt || n.id === this.product()?.niche?.id),
  );

  /** Fornecedores para escolher: os ativos e os arquivados que já têm preço aqui. */
  protected readonly supplierChoices = computed(() => {
    const active = (this.supplierOptions.value() ?? []).map((s) => ({
      id: s.id,
      name: s.name,
      archived: false,
    }));
    const known = new Set(active.map((s) => s.id));
    const archived = (this.product()?.offers ?? [])
      .filter((o) => !known.has(o.supplierId))
      .map((o) => ({ id: o.supplierId, name: o.supplierName ?? 'Fornecedor', archived: true }));
    return [...active, ...archived];
  });

  /** O menor preço entre o que está nos campos agora (fornecedor arquivado não conta). */
  protected readonly lowestKey = computed(() => {
    this.revision();
    const archived = new Set(
      this.supplierChoices()
        .filter((s) => s.archived)
        .map((s) => s.id),
    );
    let best: { key: number; price: number } | null = null;
    for (const r of this.rows) {
      const price = parseUnitPrice(r.unitPrice);
      if (price == null || !r.supplierId || archived.has(r.supplierId)) continue;
      if (!best || price < best.price) best = { key: r.key, price };
    }
    return best?.key ?? null;
  });

  protected readonly invalid = computed(() => {
    this.revision();
    const set = new Set<string>();
    for (const r of this.rows) {
      if (r.unitPrice.trim() && parseUnitPrice(r.unitPrice) == null) set.add(`p${r.key}`);
      if (r.minimumOrder.trim() && parseQuantity(r.minimumOrder) == null) set.add(`m${r.key}`);
      if (r.leadTimeDays.trim() && parseDays(r.leadTimeDays) == null) set.add(`d${r.key}`);
    }
    return set;
  });

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.id = params.get('id') ?? '';
      this.product.set(null);
      this.saver.reset();
      this.load();
    });
    const destroy = inject(DestroyRef);
    destroy.onDestroy(() => {
      this.saver.flushPending();
      Object.values(this.thumbs()).forEach((url) => URL.revokeObjectURL(url));
    });
  }

  private load(): void {
    this.api.getProduct(this.id).subscribe({
      next: (p) => this.fill(p),
      error: (err: unknown) =>
        this.loadError.set(
          err instanceof ApiError ? err.detail : 'Não foi possível abrir o produto.',
        ),
    });
  }

  private fill(p: ProductResponse): void {
    this.product.set(p);
    this.form = {
      name: p.name,
      description: p.description ?? '',
      nicheId: p.niche?.id ?? '',
      unit: p.unit ?? '',
      brand: p.brand ?? '',
      catalogCode: p.catalogCode ?? '',
    };
    this.rows = p.offers.map((o) => ({
      key: this.nextKey++,
      supplierId: o.supplierId,
      unitPrice: formatUnitPrice(o.unitPrice ?? null),
      quotedOn: o.quotedOn ?? '',
      leadTimeDays: o.leadTimeDays == null ? '' : String(o.leadTimeDays),
      minimumOrder: formatQuantity(o.minimumOrder ?? null),
    }));
    this.revision.update((n) => n + 1);
    this.loadThumbs(p.files);
  }

  /** Depois de salvar: a data que o servidor pôs (preço novo) aparece, se a pessoa não mexeu nela nesse meio-tempo. */
  private saved(p: ProductResponse): void {
    this.product.set(p);
    for (const r of this.rows) {
      const offer = p.offers.find((o) => o.supplierId === r.supplierId);
      if (offer && this.sentDates.get(r.supplierId) === r.quotedOn) {
        r.quotedOn = offer.quotedOn ?? '';
      }
    }
    this.revision.update((n) => n + 1);
  }

  protected changed(): void {
    if (!this.editable()) return;
    this.revision.update((n) => n + 1);
    this.saver.changed();
  }

  private request(): ProductRequest {
    const f = this.form;
    const text = (v: string) => v.trim() || undefined;
    const rows = this.rows.filter((r) => r.supplierId);
    this.sentDates = new Map(rows.map((r) => [r.supplierId, r.quotedOn]));
    return {
      name: text(f.name),
      description: text(f.description),
      nicheId: f.nicheId || undefined,
      unit: text(f.unit),
      brand: text(f.brand),
      catalogCode: text(f.catalogCode),
      offers: rows.map((r) => ({
        supplierId: r.supplierId,
        unitPrice: parseUnitPrice(r.unitPrice) ?? undefined,
        quotedOn: r.quotedOn || undefined,
        leadTimeDays: parseDays(r.leadTimeDays) ?? undefined,
        minimumOrder: parseQuantity(r.minimumOrder) ?? undefined,
      })),
    };
  }

  protected addOffer(): void {
    const key = this.nextKey++;
    this.rows = [
      ...this.rows,
      { key, supplierId: '', unitPrice: '', quotedOn: '', leadTimeDays: '', minimumOrder: '' },
    ];
    this.revision.update((n) => n + 1);
    setTimeout(() => document.getElementById(`of-supplier-${key}`)?.focus());
  }

  protected removeOffer(row: OfferRow): void {
    this.rows = this.rows.filter((r) => r !== row);
    this.changed();
  }

  /** Fornecedor já usado em outra linha não aparece de novo na escolha. */
  protected usedElsewhere(row: OfferRow, supplierId: string): boolean {
    return this.rows.some((r) => r !== row && r.supplierId === supplierId);
  }

  protected tidyRow(row: OfferRow): void {
    const price = parseUnitPrice(row.unitPrice);
    if (price != null) row.unitPrice = formatUnitPrice(price);
    const min = parseQuantity(row.minimumOrder);
    if (min != null) row.minimumOrder = formatQuantity(min);
  }

  protected isStale(row: OfferRow): boolean {
    if (!row.quotedOn) return false;
    const limit = new Date();
    limit.setDate(limit.getDate() - STALE_AFTER_DAYS);
    return new Date(`${row.quotedOn}T00:00:00`) < new Date(limit.toDateString());
  }

  protected supplierName(id: string): string {
    return this.supplierChoices().find((s) => s.id === id)?.name ?? '';
  }

  // ---- arquivos ----

  protected upload(input: HTMLInputElement): void {
    const file = input.files?.[0];
    if (!file) return;
    this.busy.set(true);
    this.actionError.set(null);
    this.api.uploadProductFile(this.id, { kind: this.uploadKind }, { file }).subscribe({
      next: (f) => {
        this.busy.set(false);
        input.value = '';
        const p = this.product();
        if (p) this.product.set({ ...p, files: [f, ...p.files] });
        this.loadThumbs([f]);
      },
      error: (err: unknown) => {
        input.value = '';
        this.fail(err);
      },
    });
  }

  protected download(f: ProductFileResponse): void {
    this.api.downloadProductFile(this.id, f.id).subscribe({
      next: (blob) => saveBlob(blob, f.fileName),
      error: (err: unknown) => this.fail(err),
    });
  }

  protected removeFile(f: ProductFileResponse): void {
    this.busy.set(true);
    this.actionError.set(null);
    this.api.deleteProductFile(this.id, f.id).subscribe({
      next: () => {
        this.busy.set(false);
        const p = this.product();
        if (p) this.product.set({ ...p, files: p.files.filter((x) => x.id !== f.id) });
        const url = this.thumbs()[f.id];
        if (url) URL.revokeObjectURL(url);
      },
      error: (err: unknown) => this.fail(err),
    });
  }

  private loadThumbs(files: ProductFileResponse[]): void {
    for (const f of files.filter((x) => x.contentType.startsWith('image/'))) {
      if (this.thumbs()[f.id]) continue;
      this.api.downloadProductFile(this.id, f.id).subscribe({
        next: (blob) => this.thumbs.update((t) => ({ ...t, [f.id]: URL.createObjectURL(blob) })),
        error: () => undefined,
      });
    }
  }

  protected toggleArchive(): void {
    const p = this.product();
    if (!p) return;
    this.saver.flushPending();
    if (!this.saver.idle()) {
      this.actionError.set('Espere terminar de salvar e tente de novo.');
      return;
    }
    this.busy.set(true);
    this.actionError.set(null);
    const call = p.archivedAt
      ? this.api.restoreProduct(p.id, { version: p.version })
      : this.api.archiveProduct(p.id, { version: p.version });
    call.subscribe({
      next: (v) => {
        this.busy.set(false);
        this.product.set(v);
      },
      error: (err: unknown) => this.fail(err),
    });
  }

  protected reloadPage(): void {
    this.saver.reset();
    this.load();
  }

  private fail(err: unknown): void {
    this.busy.set(false);
    this.actionError.set(err instanceof ApiError ? err.detail : 'Não foi possível concluir.');
  }
}

function emptyForm(): Form {
  return { name: '', description: '', nicheId: '', unit: '', brand: '', catalogCode: '' };
}
