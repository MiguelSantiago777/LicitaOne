import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Painel. Os indicadores vêm da API na Fase 5; até lá mostra os zeros reais de uma base vazia. */
@Component({
  selector: 'app-dashboard-page',
  templateUrl: './dashboard.page.html',
  styleUrl: './dashboard.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {}
