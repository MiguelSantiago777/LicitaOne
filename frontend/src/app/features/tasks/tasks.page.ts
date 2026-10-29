import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, map } from 'rxjs';
import type {
  SearchTasksParams,
  SearchTasksSituation,
  TaskResponse,
} from '../../api/generated/model';
import { TasksService } from '../../api/generated/tasks/tasks.service';
import { Session } from '../../core/auth/session';
import { ApiError } from '../../core/http/api-error';
import { Avatar } from '../../shared/ui/avatar';
import { Icon } from '../../shared/ui/icon';
import { Pager } from '../../shared/ui/pager';
import { TaskDialog } from './task-dialog';

const PAGE_SIZE = 25;

const SITUATIONS: { id: SearchTasksSituation; label: string }[] = [
  { id: 'ABERTAS', label: 'Abertas' },
  { id: 'ATRASADAS', label: 'Atrasadas' },
  { id: 'CONCLUIDAS', label: 'Concluídas' },
  { id: 'TODAS', label: 'Todas' },
];

/**
 * Lista de tarefas no estilo do print 3.1. Situação, busca, "Minhas tarefas", ordem e página ficam na URL. A
 * conclusão em massa é tudo ou nada (o servidor recusa se uma das tarefas for de outra pessoa).
 */
@Component({
  selector: 'app-tasks-page',
  imports: [CurrencyPipe, DatePipe, FormsModule, RouterLink, Avatar, Icon, Pager, TaskDialog],
  templateUrl: './tasks.page.html',
  styleUrl: './tasks.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TasksPage {
  private readonly api = inject(TasksService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly session = inject(Session);

  protected readonly situations = SITUATIONS;
  protected readonly pageSize = PAGE_SIZE;

  private readonly params = toSignal(this.route.queryParamMap.pipe(map((p) => p)), {
    initialValue: this.route.snapshot.queryParamMap,
  });
  protected readonly situation = computed(
    () => (this.params().get('situacao') ?? 'ABERTAS') as SearchTasksSituation,
  );
  protected readonly q = computed(() => this.params().get('q') ?? '');
  protected readonly mine = computed(() => this.params().get('minhas') === '1');
  protected readonly page = computed(() => Number(this.params().get('pagina') ?? 0));
  protected readonly direction = computed(() =>
    this.params().get('direcao') === 'desc' ? 'desc' : 'asc',
  );

  protected readonly list = rxResource({
    params: computed((): SearchTasksParams => ({
      situation: this.situation(),
      q: this.q() || undefined,
      mine: this.mine() || undefined,
      page: this.page(),
      size: PAGE_SIZE,
      direction: this.direction(),
    })),
    stream: ({ params }) => this.api.searchTasks(params),
  });
  protected readonly summary = rxResource({
    params: computed(() => ({ mine: this.mine() || undefined })),
    stream: ({ params }) => this.api.taskSummary(params),
  });

  protected readonly canCreate = computed(() => this.session.user()?.role !== 'VIEWER');
  protected readonly selected = signal<Set<string>>(new Set());
  protected readonly selectable = computed(() =>
    (this.list.value()?.items ?? []).filter((t) => t.canEdit && t.status !== 'CONCLUIDA'),
  );
  protected readonly allSelected = computed(
    () => this.selectable().length > 0 && this.selectable().every((t) => this.selected().has(t.id)),
  );
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly dialogOpen = signal(false);
  protected readonly editing = signal<TaskResponse | null>(null);
  protected readonly search$ = new Subject<string>();
  protected search = this.route.snapshot.queryParamMap.get('q') ?? '';

  constructor() {
    this.search$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((q) => this.navigate({ q: q.trim() || null }));
  }

  protected navigate(changes: Record<string, string | number | null>, resetPage = true): void {
    this.selected.set(new Set());
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: resetPage ? { ...changes, pagina: changes['pagina'] ?? null } : changes,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected countFor(id: SearchTasksSituation): number | null {
    const s = this.summary.value();
    if (!s) return null;
    if (id === 'ABERTAS') return s.open;
    if (id === 'ATRASADAS') return s.overdue;
    return null;
  }

  protected toggleSort(): void {
    this.navigate({ direcao: this.direction() === 'asc' ? 'desc' : null });
  }

  protected toggle(id: string): void {
    this.selected.update((set) => {
      const next = new Set(set);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  protected toggleAll(): void {
    this.selected.set(this.allSelected() ? new Set() : new Set(this.selectable().map((t) => t.id)));
  }

  protected completeSelected(): void {
    const ids = [...this.selected()];
    if (!ids.length) return;
    this.busy.set(true);
    this.error.set(null);
    this.api.completeTasks({ ids }).subscribe({
      next: (r) => {
        this.busy.set(false);
        this.selected.set(new Set());
        this.notice.set(
          r.completed === 1 ? '1 tarefa concluída.' : `${r.completed} tarefas concluídas.`,
        );
        this.reload();
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível concluir.');
      },
    });
  }

  protected openNew(): void {
    this.editing.set(null);
    this.dialogOpen.set(true);
  }

  protected openTask(task: TaskResponse): void {
    this.editing.set(task);
    this.dialogOpen.set(true);
  }

  protected onSaved(): void {
    this.dialogOpen.set(false);
    this.editing.set(null);
    this.reload();
  }

  private reload(): void {
    this.list.reload();
    this.summary.reload();
  }
}
