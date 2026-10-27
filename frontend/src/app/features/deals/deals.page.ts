import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Funil de negociações. As colunas serão editáveis pelos usuários (Fase 4); aqui, as etapas iniciais. */
@Component({
  selector: 'app-deals-page',
  templateUrl: './deals.page.html',
  styleUrl: './deals.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DealsPage {
  protected readonly stages = [
    'Sem contato',
    'Contato feito',
    'Identificação do interesse',
    'Apresentação',
    'Proposta enviada',
    'Fechado ganho',
    'Fechado perdido',
  ];
}
