import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { Avatar } from '../../shared/ui/avatar';
import { Icon } from '../../shared/ui/icon';
import { DemoTask, TASKS, daysBetween } from '../demo/demo-data';

@Component({
  selector: 'app-tasks-page',
  imports: [CurrencyPipe, DatePipe, Avatar, Icon],
  templateUrl: './tasks.page.html',
  styleUrl: './tasks.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TasksPage {
  protected readonly tasks = signal<DemoTask[]>(TASKS.map((t) => ({ ...t })));
  protected readonly selected = signal<Set<string>>(new Set());
  protected readonly overdueCount = computed(
    () => this.tasks().filter((t) => this.isOverdue(t)).length,
  );
  protected readonly openCount = computed(() => this.tasks().filter((t) => !t.done).length);

  protected isOverdue(t: DemoTask): boolean {
    return !t.done && daysBetween(t.dueAt) > 0;
  }

  protected toggle(id: string): void {
    this.selected.update((set) => {
      const next = new Set(set);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  protected completeSelected(): void {
    const ids = this.selected();
    this.tasks.update((list) => list.map((t) => (ids.has(t.id) ? { ...t, done: true } : t)));
    this.selected.set(new Set());
  }
}
