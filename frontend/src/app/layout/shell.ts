import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AssistantService } from '../api/generated/assistant/assistant.service';
import { TendersService } from '../api/generated/tenders/tenders.service';
import { Session } from '../core/auth/session';
import { AssistantPanel } from '../features/assistant/assistant-panel';
import { Avatar } from '../shared/ui/avatar';
import { BrandMark } from '../shared/ui/brand-mark';
import { Icon } from '../shared/ui/icon';
import { PasswordDialog } from './password-dialog';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  badge?: number;
  badgeLabel?: string;
}

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    Icon,
    BrandMark,
    Avatar,
    PasswordDialog,
    AssistantPanel,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Shell {
  protected readonly session = inject(Session);
  private readonly router = inject(Router);
  private readonly tendersApi = inject(TendersService);
  private readonly assistantApi = inject(AssistantService);

  protected readonly menuOpen = signal(false);
  protected readonly passwordOpen = signal(false);
  protected readonly assistantOpen = signal(false);

  /** "Pergunte ao LicitaOne" (Fase 11): o botão só aparece com a IA configurada. */
  private readonly assistant = rxResource({
    stream: () => this.assistantApi.assistantSettings(),
  });
  protected readonly assistantEnabled = computed(() => this.assistant.value()?.enabled ?? false);

  /** Contador real do menu: licitações vencidas que ainda esperam contrato. */
  private readonly summary = rxResource({ stream: () => this.tendersApi.tenderSummary() });
  private readonly awaitingContract = computed(() =>
    (this.summary.value() ?? [])
      .filter((s) => s.status === 'VENCIDA' || s.status === 'AGUARDANDO_ASSINATURA')
      .reduce((a, s) => a + s.count, 0),
  );

  protected readonly isViewer = computed(() => this.session.user()?.role === 'VIEWER');

  protected readonly groups = computed<{ label: string | null; items: NavItem[] }[]>(() => {
    const groups: { label: string | null; items: NavItem[] }[] = [
      { label: null, items: [{ path: '/painel', label: 'Painel', icon: 'dashboard' }] },
      {
        label: 'Contratos',
        items: [
          {
            path: '/licitacoes',
            label: 'Licitações',
            icon: 'tenders',
            badge: this.awaitingContract(),
            badgeLabel: 'vencidas aguardando contrato',
          },
          { path: '/orgaos', label: 'Órgãos', icon: 'landmark' },
          { path: '/atas', label: 'Atas', icon: 'records' },
        ],
      },
      {
        label: 'Comercial',
        items: [
          { path: '/quadros', label: 'Quadros', icon: 'columns' },
          { path: '/negociacoes', label: 'Negociações', icon: 'deals' },
          { path: '/propostas', label: 'Propostas', icon: 'proposals' },
          { path: '/tarefas', label: 'Tarefas', icon: 'tasks' },
          { path: '/alertas', label: 'Alertas', icon: 'bell' },
        ],
      },
      { label: 'Dados', items: [{ path: '/importacao', label: 'Importação', icon: 'imports' }] },
    ];
    if (this.session.user()?.role === 'ADMIN') {
      groups.push({
        label: 'Administração',
        items: [{ path: '/usuarios', label: 'Usuários', icon: 'settings' }],
      });
    }
    return groups;
  });

  constructor() {
    this.router.events
      .pipe(
        filter((e) => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        this.menuOpen.set(false);
        this.summary.reload();
      });
  }

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  protected search(event: Event): void {
    event.preventDefault();
    const input = (event.target as HTMLFormElement).elements.namedItem('q') as HTMLInputElement;
    void this.router.navigate(['/licitacoes'], {
      queryParams: { q: input.value.trim() || null, aba: null },
    });
  }

  protected newTender(): void {
    // Já na lista, mantém aba, filtros e página por trás do formulário
    const onList = this.router.url.split('?')[0] === '/licitacoes';
    void this.router.navigate(['/licitacoes'], {
      queryParams: { novo: 1 },
      queryParamsHandling: onList ? 'merge' : '',
    });
  }

  protected logout(): void {
    this.session.logout();
  }
}
