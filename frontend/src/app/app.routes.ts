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
    title: 'Entrar · LicitaOne',
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
        title: 'Painel · LicitaOne',
        loadComponent: () =>
          import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
      },
      {
        path: 'licitacoes',
        title: 'Licitações · LicitaOne',
        loadComponent: () => import('./features/tenders/tenders.page').then((m) => m.TendersPage),
      },
      {
        path: 'licitacoes/:id',
        title: 'Licitação · LicitaOne',
        loadComponent: () =>
          import('./features/tenders/tender-detail.page').then((m) => m.TenderDetailPage),
      },
      {
        path: 'orgaos',
        title: 'Órgãos · LicitaOne',
        loadComponent: () =>
          import('./features/agencies/agencies.page').then((m) => m.AgenciesPage),
      },
      {
        path: 'atas',
        title: 'Atas · LicitaOne',
        loadComponent: () =>
          import('./features/price-records/price-records.page').then((m) => m.PriceRecordsPage),
      },
      {
        path: 'quadros',
        title: 'Quadros · LicitaOne',
        loadComponent: () => import('./features/boards/boards.page').then((m) => m.BoardsPage),
      },
      {
        path: 'negociacoes',
        title: 'Negociações · LicitaOne',
        loadComponent: () => import('./features/deals/deals.page').then((m) => m.DealsPage),
      },
      {
        path: 'tarefas',
        title: 'Tarefas · LicitaOne',
        loadComponent: () => import('./features/tasks/tasks.page').then((m) => m.TasksPage),
      },
      {
        path: 'importacao',
        title: 'Importação · LicitaOne',
        loadComponent: () => import('./features/imports/imports.page').then((m) => m.ImportsPage),
      },
      {
        path: 'usuarios',
        canMatch: [adminGuard],
        title: 'Usuários · LicitaOne',
        loadComponent: () => import('./features/users/users.page').then((m) => m.UsersPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
