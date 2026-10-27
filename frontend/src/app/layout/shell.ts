import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Session } from '../core/auth/session';
import { BrandMark } from '../shared/ui/brand-mark';
import { Icon } from '../shared/ui/icon';

interface NavItem {
  path: string;
  label: string;
  icon: string;
}

interface NavGroup {
  label: string | null;
  items: NavItem[];
}

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon, BrandMark],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Shell {
  protected readonly session = inject(Session);
  protected readonly menuOpen = signal(false);

  protected readonly groups: NavGroup[] = [
    { label: null, items: [{ path: '/painel', label: 'Painel', icon: 'dashboard' }] },
    {
      label: 'Contratos',
      items: [
        { path: '/licitacoes', label: 'Licitações', icon: 'tenders' },
        { path: '/atas', label: 'Atas', icon: 'records' },
      ],
    },
    {
      label: 'Comercial',
      items: [
        { path: '/negociacoes', label: 'Negociações', icon: 'deals' },
        { path: '/tarefas', label: 'Tarefas', icon: 'tasks' },
      ],
    },
    { label: 'Dados', items: [{ path: '/importacao', label: 'Importação', icon: 'imports' }] },
  ];

  constructor() {
    // No celular, o menu fecha ao navegar
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
