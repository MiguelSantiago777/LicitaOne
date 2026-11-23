import { CurrencyPipe } from '@angular/common';
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
import type { SupplierRequest, SupplierResponse } from '../../api/generated/model';
import { ApiError } from '../../core/http/api-error';
import { UFS } from '../../shared/format';
import { Icon } from '../../shared/ui/icon';
import { formatCnpj } from '../proposals/proposal-format';
import { Autosave } from './autosave';
import { SaveStatus } from './save-status';
import { formatDay } from './catalog-format';

interface ContactRow {
  key: number;
  name: string;
  phone: string;
  email: string;
}

interface Form {
  legalName: string;
  tradeName: string;
  cnpj: string;
  city: string;
  state: string;
  website: string;
  notes: string;
}

/**
 * Edição do fornecedor: dados, nichos que atende e contatos, salvando sozinha. Embaixo, os produtos que ele vende,
 * com o preço dele e se é o menor. Perfil Leitura: só consulta.
 */
@Component({
  selector: 'app-supplier-page',
  imports: [CurrencyPipe, FormsModule, RouterLink, Icon, SaveStatus],
  templateUrl: './supplier.page.html',
  styleUrl: './catalog-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SupplierPage {
  private readonly api = inject(CatalogService);
  private readonly route = inject(ActivatedRoute);

  protected readonly ufs = UFS;
  protected readonly formatDay = formatDay;
  private id = '';

  protected readonly supplier = signal<SupplierResponse | null>(null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly niches = rxResource({
    stream: () => this.api.listNiches({ includeArchived: true }),
  });

  protected form: Form = emptyForm();
  protected contacts: ContactRow[] = [];
  protected readonly nicheIds = signal<Set<string>>(new Set());
  private nextKey = 1;

  protected readonly saver = new Autosave<SupplierResponse>(
    () => this.supplier(),
    (version) => this.api.updateSupplier(this.id, { version, supplier: this.request() }),
    (s) => this.supplier.set(s),
  );

  protected readonly busy = signal(false);
  protected readonly actionError = signal<string | null>(null);

  protected readonly editable = computed(
    () => (this.supplier()?.canEdit ?? false) && !this.supplier()?.archivedAt,
  );

  /** Nichos que dá para marcar: os ativos e os arquivados que o fornecedor já tem. */
  protected readonly nicheChoices = computed(() =>
    (this.niches.value() ?? []).filter((n) => !n.archivedAt || this.nicheIds().has(n.id)),
  );

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.id = params.get('id') ?? '';
      this.supplier.set(null);
      this.saver.reset();
      this.load();
    });
    inject(DestroyRef).onDestroy(() => this.saver.flushPending());
  }

  private load(): void {
    this.api.getSupplier(this.id).subscribe({
      next: (s) => this.fill(s),
      error: (err: unknown) =>
        this.loadError.set(
          err instanceof ApiError ? err.detail : 'Não foi possível abrir o fornecedor.',
        ),
    });
  }

  private fill(s: SupplierResponse): void {
    this.supplier.set(s);
    this.form = {
      legalName: s.legalName,
      tradeName: s.tradeName ?? '',
      cnpj: formatCnpj(s.cnpj),
      city: s.city ?? '',
      state: s.state ?? '',
      website: s.website ?? '',
      notes: s.notes ?? '',
    };
    this.contacts = s.contacts.map((c) => ({
      key: this.nextKey++,
      name: c.name ?? '',
      phone: c.phone ?? '',
      email: c.email ?? '',
    }));
    this.nicheIds.set(new Set(s.niches.map((n) => n.id)));
  }

  protected changed(): void {
    if (this.editable()) this.saver.changed();
  }

  protected toggleNiche(id: string, on: boolean): void {
    this.nicheIds.update((set) => {
      const next = new Set(set);
      if (on) next.add(id);
      else next.delete(id);
      return next;
    });
    this.changed();
  }

  protected addContact(): void {
    const key = this.nextKey++;
    this.contacts = [...this.contacts, { key, name: '', phone: '', email: '' }];
    setTimeout(() => document.getElementById(`ct-name-${key}`)?.focus());
  }

  protected removeContact(row: ContactRow): void {
    this.contacts = this.contacts.filter((c) => c !== row);
    this.changed();
  }

  protected tidyCnpj(): void {
    this.form.cnpj = formatCnpj(this.form.cnpj);
  }

  private request(): SupplierRequest {
    const f = this.form;
    const text = (v: string) => v.trim() || undefined;
    return {
      legalName: text(f.legalName),
      tradeName: text(f.tradeName),
      cnpj: text(f.cnpj),
      city: text(f.city),
      state: text(f.state),
      website: text(f.website),
      notes: text(f.notes),
      nicheIds: [...this.nicheIds()],
      contacts: this.contacts.map((c) => ({
        name: text(c.name),
        phone: text(c.phone),
        email: text(c.email),
      })),
    };
  }

  protected toggleArchive(): void {
    const s = this.supplier();
    if (!s) return;
    this.saver.flushPending();
    if (!this.saver.idle()) {
      this.actionError.set('Espere terminar de salvar e tente de novo.');
      return;
    }
    this.busy.set(true);
    this.actionError.set(null);
    const call = s.archivedAt
      ? this.api.restoreSupplier(s.id, { version: s.version })
      : this.api.archiveSupplier(s.id, { version: s.version });
    call.subscribe({
      next: (v) => {
        this.busy.set(false);
        this.supplier.set(v);
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.actionError.set(err instanceof ApiError ? err.detail : 'Não foi possível concluir.');
      },
    });
  }

  protected reloadPage(): void {
    this.saver.reset();
    this.load();
  }
}

function emptyForm(): Form {
  return { legalName: '', tradeName: '', cnpj: '', city: '', state: '', website: '', notes: '' };
}
