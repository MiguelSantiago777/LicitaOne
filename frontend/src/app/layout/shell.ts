import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { Session } from '../core/auth/session';
import { TASKS, TENDERS, daysBetween } from '../features/demo/demo-data';
import { Avatar } from '../shared/ui/avatar';
import { BrandMark } from '../shared/ui/brand-mark';
import { Icon } from '../shared/ui/icon';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  badge?: number;
}

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon, BrandMark, Avatar],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Shell {
  protected readonly session = inject(Session);
  protected readonly menuOpen = signal(false);

  private readonly overdueTasks = TASKS.filter((t) => !t.done && daysBetween(t.dueAt) > 0).length;
  private readonly awaitingContract = TENDERS.filter((t) => t.status === 'VENCIDA').length;

  protected readonly groups: { label: string | null; items: NavItem[] }[] = [
    { label: null, items: [{ path: '/painel', label: 'Painel', icon: 'dashboard' }] },
    {
      label: 'Contratos',
      items: [
        { path: '/licitacoes', label: 'Licitações', icon: 'tenders', badge: this.awaitingContract },
        { path: '/atas', label: 'Atas', icon: 'records' },
      ],
    },
    {
      label: 'Comercial',
      items: [
        { path: '/negociacoes', label: 'Negociações', icon: 'deals' },
        { path: '/tarefas', label: 'Tarefas', icon: 'tasks', badge: this.overdueTasks },
      ],
    },
    { label: 'Dados', items: [{ path: '/importacao', label: 'Importação', icon: 'imports' }] },
  ];

  protected readonly firstName = computed(() => this.session.user()?.name.split(' ')[0] ?? '');

  constructor() {
    inject(Router)
      .events.pipe(
        filter((e) => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.menuOpen.set(false));
  }

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  protected logout(): void {
    this.session.logout();
  }
}
