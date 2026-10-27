import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { Icon } from '../../shared/ui/icon';

interface LifecycleTab {
  id: string;
  label: string;
  empty: string;
}

/** Licitações nas quatro abas do ciclo de vida. Cadastro, filtros e detalhe entram na Fase 1. */
@Component({
  selector: 'app-tenders-page',
  imports: [Icon],
  templateUrl: './tenders.page.html',
  styleUrl: './tenders.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TendersPage {
  protected readonly tabs: LifecycleTab[] = [
    { id: 'vencidas', label: 'Vencidas', empty: 'Nenhuma licitação vencida aguardando contrato.' },
    {
      id: 'assinados',
      label: 'Contratos assinados',
      empty: 'Nenhum contrato assinado sem pedido.',
    },
    { id: 'andamento', label: 'Em andamento', empty: 'Nenhum contrato com pedidos em andamento.' },
    { id: 'encerrados', label: 'Encerrados', empty: 'Nenhum contrato encerrado.' },
  ];
  protected readonly selected = signal(this.tabs[0]);
}
