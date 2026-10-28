import { inject } from '@angular/core';
import { CanMatchFn, Router, Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth/auth.guards';
import { Session } from './core/auth/session';

const adminGuard: CanMatchFn = () =>
  inject(Session).user()?.role === 'ADMIN' || inject(Router).createUrlTree(['/painel']);

export const routes: Routes = [
  {
    path: 'login',
    canMatch: [guestGuard],
    title: 'Entrar · ShemaCRM',
    loadComponent: () => import('./features/auth/login.page').then((m) => m.LoginPage),
  },
  {
    path: '',
    canMatch: [authGuard],
    loadComponent: () => import('./layout/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'painel' },
      {
        path: 'painel',
        title: 'Painel · ShemaCRM',
        loadComponent: () =>
          import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
      },
      {
        path: 'licitacoes',
        title: 'Licitações · ShemaCRM',
        loadComponent: () => import('./features/tenders/tenders.page').then((m) => m.TendersPage),
      },
      {
        path: 'licitacoes/:id',
        title: 'Licitação · ShemaCRM',
        loadComponent: () =>
          import('./features/tenders/tender-detail.page').then((m) => m.TenderDetailPage),
      },
      {
        path: 'orgaos',
        title: 'Órgãos · ShemaCRM',
        loadComponent: () =>
          import('./features/agencies/agencies.page').then((m) => m.AgenciesPage),
      },
      {
        path: 'atas',
        title: 'Atas · ShemaCRM',
        loadComponent: () =>
          import('./features/price-records/price-records.page').then((m) => m.PriceRecordsPage),
      },
      {
        path: 'quadros',
        title: 'Quadros · ShemaCRM',
        loadComponent: () => import('./features/boards/boards.page').then((m) => m.BoardsPage),
      },
      {
        path: 'negociacoes',
        title: 'Negociações · ShemaCRM',
        loadComponent: () => import('./features/deals/deals.page').then((m) => m.DealsPage),
      },
      {
        path: 'tarefas',
        title: 'Tarefas · ShemaCRM',
        loadComponent: () => import('./features/tasks/tasks.page').then((m) => m.TasksPage),
      },
      {
        path: 'importacao',
        title: 'Importação · ShemaCRM',
        loadComponent: () => import('./features/imports/imports.page').then((m) => m.ImportsPage),
      },
      {
        path: 'usuarios',
        canMatch: [adminGuard],
        title: 'Usuários · ShemaCRM',
        loadComponent: () => import('./features/users/users.page').then((m) => m.UsersPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
