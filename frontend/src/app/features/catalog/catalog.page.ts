import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { CatalogService } from '../../api/generated/catalog/catalog.service';
import type {
  NicheResponse,
  SearchProductsParams,
  SearchSuppliersParams,
} from '../../api/generated/model';
import { Session } from '../../core/auth/session';
import { ApiError } from '../../core/http/api-error';
import { UFS } from '../../shared/format';
import { Icon } from '../../shared/ui/icon';
import { Pager } from '../../shared/ui/pager';
import { formatCnpj } from '../proposals/proposal-format';
import { formatDay } from './catalog-format';

const PAGE_SIZE = 25;

export type CatalogTab = 'produtos' | 'fornecedores' | 'nichos';

export const TABS: { id: CatalogTab; label: string }[] = [
  { id: 'produtos', label: 'Produtos' },
  { id: 'fornecedores', label: 'Fornecedores' },
  { id: 'nichos', label: 'Nichos' },
];

/**
 * Fornecedores e produtos por nicho (Fase 13). Três abas na mesma tela; aba, nicho, busca, UF, arquivados e página
 * ficam na URL. O filtro de nicho vale para as três. "Novo produto" e "Novo fornecedor" pedem só o essencial, criam
 * no servidor e abrem a tela de edição.
 */
@Component({
  selector: 'app-catalog-page',
  imports: [CurrencyPipe, FormsModule, RouterLink, Icon, Pager],
  templateUrl: './catalog.page.html',
  styleUrl: './catalog.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CatalogPage {
  private readonly api = inject(CatalogService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly session = inject(Session);

  protected readonly tabs = TABS;
  protected readonly ufs = UFS;
  protected readonly pageSize = PAGE_SIZE;
  protected readonly formatCnpj = formatCnpj;
  protected readonly formatDay = formatDay;

  private readonly params = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });
  protected readonly tab = computed<CatalogTab>(() => {
    const t = this.params().get('aba');
    return t === 'fornecedores' || t === 'nichos' ? t : 'produtos';
  });
  protected readonly nicheId = computed(() => this.params().get('nicho') ?? '');
  protected readonly q = computed(() => this.params().get('q') ?? '');
  protected readonly uf = computed(() => this.params().get('uf') ?? '');
  protected readonly archived = computed(() => this.params().get('arquivados') === '1');
  protected readonly page = computed(() => Number(this.params().get('pagina') ?? 0));

  protected readonly canEdit = computed(() => this.session.user()?.role !== 'VIEWER');

  /** Todos os nichos, inclusive arquivados: a aba Nichos mostra todos; os filtros, só os ativos. */
  protected readonly niches = rxResource({
    stream: () => this.api.listNiches({ includeArchived: true }),
  });
  protected readonly activeNiches = computed(() =>
    (this.niches.value() ?? []).filter((n) => !n.archivedAt),
  );
  protected readonly shownNiches = computed(() =>
    (this.niches.value() ?? []).filter(
      (n) => (this.archived() || !n.archivedAt) && (!this.nicheId() || n.id === this.nicheId()),
    ),
  );

  protected readonly products = rxResource({
    params: computed((): SearchProductsParams | undefined =>
      this.tab() !== 'produtos'
        ? undefined
        : {
            q: this.q() || undefined,
            nicheId: this.nicheId() || undefined,
            includeArchived: this.archived() || undefined,
            page: this.page(),
            size: PAGE_SIZE,
          },
    ),
    stream: ({ params }) => this.api.searchProducts(params),
  });

  protected readonly suppliers = rxResource({
    params: computed((): SearchSuppliersParams | undefined =>
      this.tab() !== 'fornecedores'
        ? undefined
        : {
            q: this.q() || undefined,
            nicheId: this.nicheId() || undefined,
            state: this.uf() || undefined,
            includeArchived: this.archived() || undefined,
            page: this.page(),
            size: PAGE_SIZE,
          },
    ),
    stream: ({ params }) => this.api.searchSuppliers(params),
  });

  protected readonly search$ = new Subject<string>();
  protected search = this.route.snapshot.queryParamMap.get('q') ?? '';

  /** Formulário de criação aberto (um por vez). */
  protected readonly creating = signal<CatalogTab | null>(null);
  protected newName = '';
  protected newNiche = '';
  protected newDescription = '';
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Nicho sendo renomeado na aba Nichos. */
  protected readonly renaming = signal<string | null>(null);
  protected renameName = '';
  protected renameDescription = '';

  constructor() {
    this.search$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((q) => this.navigate({ q: q.trim() || null }));
  }

  protected navigate(changes: Record<string, string | number | null>): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { ...changes, pagina: changes['pagina'] ?? null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected selectTab(tab: CatalogTab): void {
    this.creating.set(null);
    this.renaming.set(null);
    this.error.set(null);
    this.search = '';
    this.navigate({ aba: tab === 'produtos' ? null : tab, q: null, uf: null });
  }

  /** Das contagens da aba Nichos para a lista filtrada pelo nicho. */
  protected openNiche(n: NicheResponse, tab: CatalogTab): void {
    this.search = '';
    this.navigate({ aba: tab === 'produtos' ? null : tab, nicho: n.id, q: null, arquivados: null });
  }

  protected openProduct(id: string): void {
    void this.router.navigate(['/fornecedores-produtos/produtos', id]);
  }

  protected openSupplier(id: string): void {
    void this.router.navigate(['/fornecedores-produtos/fornecedores', id]);
  }

  protected startCreate(): void {
    this.newName = '';
    this.newDescription = '';
    this.newNiche =
      this.nicheId() && this.activeNiches().some((n) => n.id === this.nicheId())
        ? this.nicheId()
        : '';
    this.error.set(null);
    this.creating.set(this.tab());
    setTimeout(() => document.getElementById('new-name')?.focus());
  }

  protected create(event: Event): void {
    event.preventDefault();
    const name = this.newName.trim();
    const tab = this.creating();
    if (!name || !tab) return;
    this.busy.set(true);
    this.error.set(null);
    const done = {
      error: (err: unknown) => {
        this.busy.set(false);
        this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível cadastrar.');
      },
    };
    if (tab === 'produtos') {
      if (!this.newNiche) {
        this.busy.set(false);
        this.error.set('Escolha o nicho do produto.');
        return;
      }
      this.api.createProduct({ name, nicheId: this.newNiche, offers: [] }).subscribe({
        next: (p) => void this.router.navigate(['/fornecedores-produtos/produtos', p.id]),
        ...done,
      });
    } else if (tab === 'fornecedores') {
      this.api
        .createSupplier({
          legalName: name,
          nicheIds: this.newNiche ? [this.newNiche] : [],
          contacts: [],
        })
        .subscribe({
          next: (s) => void this.router.navigate(['/fornecedores-produtos/fornecedores', s.id]),
          ...done,
        });
    } else {
      this.api
        .createNiche({ name, description: this.newDescription.trim() || undefined })
        .subscribe({
          next: () => {
            this.busy.set(false);
            this.creating.set(null);
            this.niches.reload();
          },
          ...done,
        });
    }
  }

  protected startRename(n: NicheResponse): void {
    this.renameName = n.name;
    this.renameDescription = n.description ?? '';
    this.error.set(null);
    this.renaming.set(n.id);
    setTimeout(() => document.getElementById(`rename-${n.id}`)?.focus());
  }

  protected rename(event: Event, n: NicheResponse): void {
    event.preventDefault();
    this.busy.set(true);
    this.api
      .updateNiche(n.id, {
        version: n.version,
        niche: { name: this.renameName, description: this.renameDescription.trim() || undefined },
      })
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.renaming.set(null);
          this.niches.reload();
        },
        error: (err: unknown) => this.fail(err),
      });
  }

  protected toggleArchive(n: NicheResponse): void {
    this.busy.set(true);
    this.error.set(null);
    const call = n.archivedAt
      ? this.api.restoreNiche(n.id, { version: n.version })
      : this.api.archiveNiche(n.id, { version: n.version });
    call.subscribe({
      next: () => {
        this.busy.set(false);
        this.niches.reload();
      },
      error: (err: unknown) => this.fail(err),
    });
  }

  protected nicheName(id: string): string {
    return this.niches.value()?.find((n) => n.id === id)?.name ?? '';
  }

  private fail(err: unknown): void {
    this.busy.set(false);
    this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível concluir.');
  }
}
