import { CdkDrag, CdkDragDrop, CdkDropList, CdkDropListGroup } from '@angular/cdk/drag-drop';
import { CurrencyPipe, DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, map } from 'rxjs';
import { DealsService } from '../../api/generated/deals/deals.service';
import type {
  DealResponse,
  DealResponseStatus,
  GetPipelineParams,
  PipelineStageResponse,
} from '../../api/generated/model';
import { ApiError } from '../../core/http/api-error';
import { Avatar } from '../../shared/ui/avatar';
import { Icon } from '../../shared/ui/icon';
import { TaskDialog, TaskPreset } from '../tasks/task-dialog';
import { DealDialog } from './deal-dialog';
import { DEAL_STATUSES, PRIORITIES, stageColor, statusLabel } from './deal-meta';
import { StagesDialog } from './stages-dialog';

/**
 * Funil de vendas. Kanban ou lista, busca, "Minhas negociações" e status ficam na URL. Arrastar move a negociação
 * de etapa; o diálogo faz o mesmo para quem usa teclado ou celular.
 */
@Component({
  selector: 'app-deals-page',
  imports: [
    CdkDropListGroup,
    CdkDropList,
    CdkDrag,
    CurrencyPipe,
    DatePipe,
    FormsModule,
    Avatar,
    Icon,
    DealDialog,
    TaskDialog,
    StagesDialog,
  ],
  templateUrl: './deals.page.html',
  styleUrl: './deals.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DealsPage {
  private readonly api = inject(DealsService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly statuses = DEAL_STATUSES;
  protected readonly statusLabel = statusLabel;

  private readonly params = toSignal(this.route.queryParamMap.pipe(map((p) => p)), {
    initialValue: this.route.snapshot.queryParamMap,
  });
  protected readonly view = computed(() =>
    this.params().get('visao') === 'lista' ? 'list' : 'kanban',
  );
  protected readonly q = computed(() => this.params().get('q') ?? '');
  protected readonly mine = computed(() => this.params().get('minhas') === '1');
  protected readonly status = computed(
    () => (this.params().get('status') ?? '') as DealResponseStatus | '',
  );

  protected readonly pipeline = rxResource({
    params: computed((): GetPipelineParams => ({
      q: this.q() || undefined,
      mine: this.mine() || undefined,
      status: this.status() ? [this.status() as DealResponseStatus] : undefined,
    })),
    stream: ({ params }) => this.api.getPipeline(params),
  });

  protected readonly columns = computed(() => {
    let open = 0;
    return (this.pipeline.value()?.stages ?? []).map((stage) => ({
      stage,
      color: stageColor(stage.kind, stage.kind === 'ABERTA' ? open++ : 0),
    }));
  });
  protected readonly rows = computed(() =>
    this.columns().flatMap((c) => c.stage.deals.map((deal) => ({ deal, color: c.color }))),
  );

  protected readonly error = signal<string | null>(null);
  protected readonly dealDialogOpen = signal(false);
  protected readonly editing = signal<DealResponse | null>(null);
  protected readonly newInStage = signal<string | null>(null);
  protected readonly taskPreset = signal<TaskPreset | null>(null);
  protected readonly stagesOpen = signal(false);
  protected readonly search$ = new Subject<string>();
  protected search = this.route.snapshot.queryParamMap.get('q') ?? '';

  constructor() {
    this.search$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((q) => this.navigate({ q: q.trim() || null }));
  }

  protected navigate(changes: Record<string, string | null>): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: changes,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected setView(view: 'kanban' | 'list'): void {
    this.navigate({ visao: view === 'list' ? 'lista' : null });
  }

  protected clearSearch(): void {
    this.search = '';
    this.navigate({ q: null });
  }

  protected stars(priority: number): boolean[] {
    return PRIORITIES.map((p) => p.value <= priority);
  }

  protected priorityLabel(priority: number): string {
    return PRIORITIES.find((p) => p.value === priority)?.label ?? '';
  }

  protected agencyOf(deal: DealResponse): string {
    return deal.agency?.name ?? deal.tender?.agencyName ?? 'Sem órgão';
  }

  // ---- diálogos ----

  protected openNew(stageId: string | null = null): void {
    this.editing.set(null);
    this.newInStage.set(stageId);
    this.dealDialogOpen.set(true);
  }

  protected openDeal(deal: DealResponse): void {
    this.editing.set(deal);
    this.dealDialogOpen.set(true);
  }

  /** A negociação pode ter mudado de etapa (e o card, de lugar): o foco volta para ele. */
  protected onDealSaved(saved: DealResponse | null): void {
    this.dealDialogOpen.set(false);
    this.editing.set(null);
    this.pipeline.reload();
    if (saved) this.focusDeal(saved.id);
  }

  protected createTask(deal: DealResponse): void {
    this.taskPreset.set({
      deal: { id: deal.id, title: deal.title, agencyName: this.agencyOf(deal), value: deal.value },
      ownerId: deal.owner?.id,
    });
  }

  protected onTaskSaved(dealId: string | undefined): void {
    this.taskPreset.set(null);
    this.pipeline.reload();
    if (dealId) this.focusDeal(dealId);
  }

  // ---- arrastar ----

  /** Arrastou: atualiza na tela na hora e confirma no servidor (que recalcula somas e status). */
  protected drop(event: CdkDragDrop<PipelineStageResponse>): void {
    const p = this.pipeline.value();
    const deal = event.item.data as DealResponse;
    if (
      !p ||
      (event.previousContainer === event.container && event.previousIndex === event.currentIndex)
    ) {
      return;
    }
    const target = event.container.data;
    this.pipeline.set({
      ...p,
      stages: p.stages.map((s) => {
        let deals = s.deals.filter((d) => d.id !== deal.id);
        if (s.id === target.id) {
          deals = [...deals];
          deals.splice(event.currentIndex, 0, { ...deal, stageId: target.id });
        }
        return {
          ...s,
          deals,
          dealCount: deals.length,
          totalValue: deals.reduce((sum, d) => sum + d.value, 0),
        };
      }),
    });
    // Com filtro, a tela mostra só parte da etapa: no servidor, a negociação entra logo depois da vizinha de cima.
    // Se ela saiu de cima da vizinha na mesma etapa, a vizinha sobe uma posição quando ela sai.
    const above = target.deals.filter((d) => d.id !== deal.id)[event.currentIndex - 1];
    const position = !above
      ? 0
      : above.stageId === deal.stageId && deal.position < above.position
        ? above.position
        : above.position + 1;
    this.api.moveDeal(deal.id, { stageId: target.id, position }).subscribe({
      next: () => this.pipeline.reload(),
      error: (err: unknown) => {
        this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível mover.');
        this.pipeline.reload();
      },
    });
  }

  /** O card só existe (ou só muda de lugar) quando o funil recarregado chega: o foco espera por ele. */
  private focusDeal(id: string): void {
    this.pendingFocus = id;
  }

  private pendingFocus: string | null = null;

  private readonly focusAfterReload = effect(() => {
    if (!this.pipeline.value() || this.pipeline.isLoading() || !this.pendingFocus) return;
    const id = this.pendingFocus;
    this.pendingFocus = null;
    afterNextRender(
      () => this.host.nativeElement.querySelector<HTMLElement>(`[data-deal="${id}"]`)?.focus(),
      { injector: this.injector },
    );
  });
}
