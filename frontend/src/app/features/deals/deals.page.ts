import { CurrencyPipe } from '@angular/common';
import { CdkDrag, CdkDragDrop, CdkDropList, CdkDropListGroup } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { DemoBadge } from '../../shared/ui/demo-badge';
import { Avatar } from '../../shared/ui/avatar';
import { Icon } from '../../shared/ui/icon';
import { DEALS, DemoDeal, STAGES, sum } from '../demo/demo-data';

/** Funil de negociações. Prévia com dados de exemplo; arrastar entre colunas já funciona (não persiste). */
@Component({
  selector: 'app-deals-page',
  imports: [DemoBadge, CurrencyPipe, CdkDropListGroup, CdkDropList, CdkDrag, Avatar, Icon],
  templateUrl: './deals.page.html',
  styleUrl: './deals.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DealsPage {
  protected readonly stages = STAGES;
  protected readonly deals = signal<DemoDeal[]>(DEALS.map((d) => ({ ...d })));
  protected readonly view = signal<'kanban' | 'list'>('kanban');

  protected readonly columns = computed(() =>
    this.stages.map((stage) => {
      const items = this.deals().filter((d) => d.stageId === stage.id);
      return { stage, items, value: sum(items.map((i) => i.value)) };
    }),
  );
  protected readonly total = computed(() => sum(this.deals().map((d) => d.value)));

  protected drop(event: CdkDragDrop<string>): void {
    const deal = event.item.data as DemoDeal;
    const stageId = event.container.data;
    this.deals.update((list) => list.map((d) => (d.id === deal.id ? { ...d, stageId } : d)));
  }

  protected stars(priority: number): boolean[] {
    return [1, 2, 3].map((n) => n <= priority);
  }
}
