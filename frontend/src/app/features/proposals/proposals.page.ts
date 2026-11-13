import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, map } from 'rxjs';
import type { SearchProposalsParams } from '../../api/generated/model';
import { ProposalsService } from '../../api/generated/proposals/proposals.service';
import { Session } from '../../core/auth/session';
import { ApiError } from '../../core/http/api-error';
import { Icon } from '../../shared/ui/icon';
import { Pager } from '../../shared/ui/pager';
import { formatCnpj, KINDS, type ProposalKind } from './proposal-format';

const PAGE_SIZE = 25;

/**
 * Lista de propostas de preços, da mais recente para a mais antiga. Busca, tipo, canceladas e página ficam na URL.
 * "Nova proposta" já cria a proposta no servidor (com o número) e abre a tela de edição.
 */
@Component({
  selector: 'app-proposals-page',
  imports: [CurrencyPipe, DatePipe, FormsModule, RouterLink, Icon, Pager],
  templateUrl: './proposals.page.html',
  styleUrl: './proposals.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProposalsPage {
  private readonly api = inject(ProposalsService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly session = inject(Session);

  protected readonly kinds = KINDS;
  protected readonly pageSize = PAGE_SIZE;
  protected readonly formatCnpj = formatCnpj;

  private readonly params = toSignal(this.route.queryParamMap.pipe(map((p) => p)), {
    initialValue: this.route.snapshot.queryParamMap,
  });
  protected readonly q = computed(() => this.params().get('q') ?? '');
  protected readonly kind = computed(() => (this.params().get('tipo') ?? '') as ProposalKind | '');
  protected readonly cancelled = computed(() => this.params().get('canceladas') === '1');
  protected readonly page = computed(() => Number(this.params().get('pagina') ?? 0));

  protected readonly list = rxResource({
    params: computed((): SearchProposalsParams => ({
      q: this.q() || undefined,
      kind: this.kind() || undefined,
      includeCancelled: this.cancelled() || undefined,
      page: this.page(),
      size: PAGE_SIZE,
    })),
    stream: ({ params }) => this.api.searchProposals(params),
  });

  protected readonly canCreate = computed(() => this.session.user()?.role !== 'VIEWER');
  protected readonly creating = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly search$ = new Subject<string>();
  protected search = this.route.snapshot.queryParamMap.get('q') ?? '';

  constructor() {
    this.search$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((q) => this.navigate({ q: q.trim() || null }));
  }

  protected open(id: string): void {
    void this.router.navigate(['/propostas', id]);
  }

  protected navigate(changes: Record<string, string | number | null>): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { ...changes, pagina: changes['pagina'] ?? null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected kindLabel(kind: string): string {
    return KINDS.find((k) => k.id === kind)?.short ?? kind;
  }

  protected create(): void {
    this.creating.set(true);
    this.error.set(null);
    this.api.createProposal().subscribe({
      next: (p) => void this.router.navigate(['/propostas', p.id]),
      error: (err: unknown) => {
        this.creating.set(false);
        this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível criar a proposta.');
      },
    });
  }
}
