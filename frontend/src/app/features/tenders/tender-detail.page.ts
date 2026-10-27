import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TendersService } from '../../api/generated/tenders/tenders.service';
import type { NextStatusResponse, TenderResponse } from '../../api/generated/model';
import { ApiError } from '../../core/http/api-error';
import { daysSince } from '../../shared/format';
import { Avatar } from '../../shared/ui/avatar';
import { Icon } from '../../shared/ui/icon';
import { TenderForm } from './tender-form';
import { ACTION_LABELS, statusTone } from './tender-meta';
import { TransitionDialog } from './transition-dialog';

type DetailTab = 'resumo' | 'itens' | 'documentos' | 'historico' | 'tarefas' | 'negociacao';

@Component({
  selector: 'app-tender-detail-page',
  imports: [CurrencyPipe, DatePipe, RouterLink, Avatar, Icon, TenderForm, TransitionDialog],
  templateUrl: './tender-detail.page.html',
  styleUrl: './tender-detail.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TenderDetailPage {
  private readonly api = inject(TendersService);

  /** Vem da rota (:id) por withComponentInputBinding. */
  readonly id = input.required<string>();

  protected readonly tone = statusTone;
  protected readonly actionLabels = ACTION_LABELS;
  protected readonly tab = signal<DetailTab>('resumo');
  protected readonly editing = signal(false);
  protected readonly transitionTarget = signal<NextStatusResponse | null>(null);

  protected readonly tender = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.getTender(params),
  });
  protected readonly history = rxResource({
    params: () => (this.tab() === 'historico' ? this.id() : undefined),
    stream: ({ params }) => this.api.tenderHistory(params),
  });

  protected readonly notFound = computed(() => {
    const err = this.tender.error();
    return err instanceof ApiError && err.code === 'NOT_FOUND';
  });

  protected readonly tabs: { id: DetailTab; label: string }[] = [
    { id: 'resumo', label: 'Resumo' },
    { id: 'itens', label: 'Itens e pedidos' },
    { id: 'documentos', label: 'Documentos' },
    { id: 'historico', label: 'Histórico' },
    { id: 'tarefas', label: 'Tarefas' },
    { id: 'negociacao', label: 'Negociação' },
  ];

  /** Linha do tempo do ciclo de vida: o que já aconteceu fica marcado. */
  protected readonly timeline = computed(() => {
    const t = this.tender.value();
    if (!t) return [];
    return [
      { label: 'Vencida', date: t.wonAt },
      { label: 'Contrato assinado', date: t.signedAt },
      { label: 'Primeiro pedido', date: t.ordersStartedAt },
      { label: 'Encerrado', date: t.closedAt },
    ];
  });

  protected onSaved(t: TenderResponse): void {
    this.editing.set(false);
    this.transitionTarget.set(null);
    this.tender.set(t);
    this.history.reload();
  }

  protected days(iso: string | null): number {
    return daysSince(iso);
  }
}
