import { CdkDrag, CdkDragDrop, CdkDropList, CdkDropListGroup } from '@angular/cdk/drag-drop';
import { CurrencyPipe, DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, map } from 'rxjs';
import { BoardsService } from '../../api/generated/boards/boards.service';
import type {
  BoardCardResponse,
  BoardColumnResponse,
  BoardResponse,
} from '../../api/generated/model';
import { ApiError } from '../../core/http/api-error';
import { Avatar } from '../../shared/ui/avatar';
import { Icon } from '../../shared/ui/icon';
import { CardDialog } from './card-dialog';

/**
 * Quadros kanban livres (Organização mensal, Licitações vencidas e os que a equipe criar). Colunas e cards são da
 * equipe: criar, renomear, reordenar, arrastar. O quadro aberto fica na URL (?quadro=).
 */
@Component({
  selector: 'app-boards-page',
  imports: [
    CdkDropListGroup,
    CdkDropList,
    CdkDrag,
    CurrencyPipe,
    DatePipe,
    FormsModule,
    Avatar,
    Icon,
    CardDialog,
  ],
  templateUrl: './boards.page.html',
  styleUrl: './boards.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BoardsPage {
  private readonly api = inject(BoardsService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  private readonly requested = toSignal(
    this.route.queryParamMap.pipe(map((p) => p.get('quadro'))),
    { initialValue: null },
  );

  protected readonly boards = rxResource({ stream: () => this.api.listBoards() });
  protected readonly selectedId = computed(
    () => this.requested() ?? this.boards.value()?.[0]?.id ?? null,
  );
  protected readonly board = rxResource({
    params: () => this.selectedId() ?? undefined,
    stream: ({ params }) => this.api.getBoard(params),
  });

  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly editingCard = signal<BoardCardResponse | null>(null);
  /** Coluna com o campo de "novo card" aberto. */
  protected readonly addingCardTo = signal<string | null>(null);
  protected readonly renamingColumn = signal<string | null>(null);
  protected readonly confirmingColumnRemoval = signal<string | null>(null);
  protected readonly addingColumn = signal(false);
  protected readonly creatingBoard = signal(false);
  protected readonly renamingBoard = signal(false);
  protected readonly confirmingBoardRemoval = signal(false);
  protected newCardTitle = '';
  protected newColumnName = '';
  protected columnName = '';
  protected boardName = '';

  protected readonly totalCards = computed(() =>
    (this.board.value()?.columns ?? []).reduce((sum, c) => sum + c.cardCount, 0),
  );

  protected open(boardId: string): void {
    this.confirmingBoardRemoval.set(false);
    this.router.navigate([], { queryParams: { quadro: boardId } });
  }

  // ---- quadro ----

  protected createBoard(): void {
    if (!this.boardName.trim()) return;
    this.run(this.api.createBoard({ name: this.boardName.trim() }), (b) => {
      this.creatingBoard.set(false);
      this.boardName = '';
      this.boards.reload();
      this.open(b.id);
    });
  }

  protected startRenameBoard(b: BoardResponse): void {
    this.boardName = b.name;
    this.renamingBoard.set(true);
  }

  protected renameBoard(b: BoardResponse): void {
    if (!this.boardName.trim()) return;
    this.run(
      this.api.renameBoard(b.id, { version: b.version, name: this.boardName.trim() }),
      () => {
        this.renamingBoard.set(false);
        this.boards.reload();
      },
    );
  }

  protected deleteBoard(b: BoardResponse): void {
    this.busy.set(true);
    this.api.deleteBoard(b.id, { version: b.version }).subscribe({
      next: () => {
        this.busy.set(false);
        this.confirmingBoardRemoval.set(false);
        this.boards.reload();
        this.router.navigate([], { queryParams: { quadro: null } });
      },
      error: (err: unknown) => this.fail(err),
    });
  }

  // ---- colunas ----

  protected addColumn(b: BoardResponse): void {
    if (!this.newColumnName.trim()) return;
    this.run(
      this.api.addBoardColumn(b.id, { version: b.version, name: this.newColumnName.trim() }),
      () => {
        this.newColumnName = '';
      },
    );
  }

  protected startRenameColumn(col: BoardColumnResponse): void {
    this.columnName = col.name;
    this.renamingColumn.set(col.id);
  }

  protected renameColumn(b: BoardResponse, col: BoardColumnResponse): void {
    if (!this.columnName.trim() || this.columnName.trim() === col.name) {
      this.renamingColumn.set(null);
      return;
    }
    this.run(
      this.api.renameBoardColumn(b.id, col.id, {
        version: b.version,
        name: this.columnName.trim(),
      }),
      () => this.renamingColumn.set(null),
    );
  }

  protected moveColumn(b: BoardResponse, col: BoardColumnResponse, delta: number): void {
    this.run(
      this.api.moveBoardColumn(b.id, col.id, {
        version: b.version,
        position: col.position + delta,
      }),
    );
  }

  protected removeColumn(b: BoardResponse, col: BoardColumnResponse): void {
    this.run(this.api.removeBoardColumn(b.id, col.id, { version: b.version }), () =>
      this.confirmingColumnRemoval.set(null),
    );
  }

  // ---- cards ----

  protected startAddCard(columnId: string): void {
    this.newCardTitle = '';
    this.addingCardTo.set(columnId);
  }

  protected addCard(b: BoardResponse, columnId: string): void {
    const title = this.newCardTitle.trim();
    if (!title) {
      this.addingCardTo.set(null);
      return;
    }
    // Continua aberto para lançar vários cards seguidos
    this.run(this.api.addBoardCard(b.id, { columnId, card: { title } }), () => {
      this.newCardTitle = '';
    });
  }

  /** Arrastou: atualiza na tela na hora e confirma no servidor (que devolve o quadro já renumerado). */
  protected drop(event: CdkDragDrop<string>): void {
    const b = this.board.value();
    const card = event.item.data as BoardCardResponse;
    if (
      !b ||
      (event.previousContainer === event.container && event.previousIndex === event.currentIndex)
    ) {
      return;
    }
    const target = event.container.data;
    this.board.set({
      ...b,
      columns: b.columns.map((col) => {
        let cards = col.cards.filter((c) => c.id !== card.id);
        if (col.id === target) {
          cards = [...cards];
          cards.splice(event.currentIndex, 0, { ...card, columnId: target });
        }
        return { ...col, cards, cardCount: cards.length };
      }),
    });
    this.api.moveBoardCard(card.id, { columnId: target, position: event.currentIndex }).subscribe({
      next: (updated) => this.board.set(updated),
      error: (err: unknown) => {
        this.fail(err);
        this.board.reload();
      },
    });
  }

  /**
   * O card pode ter mudado de coluna (e o elemento, de lugar): o foco volta para ele, e não para o início da página.
   */
  protected onCardSaved(updated: BoardResponse): void {
    const id = this.editingCard()?.id;
    this.editingCard.set(null);
    this.board.set(updated);
    afterNextRender(
      () => this.host.nativeElement.querySelector<HTMLElement>(`[data-card="${id}"]`)?.focus(),
      { injector: this.injector },
    );
  }

  private run(request: Observable<BoardResponse>, after?: (b: BoardResponse) => void): void {
    this.busy.set(true);
    this.error.set(null);
    request.subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.board.set(updated);
        after?.(updated);
      },
      error: (err: unknown) => this.fail(err),
    });
  }

  private fail(err: unknown): void {
    this.busy.set(false);
    this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível salvar.');
    if (err instanceof ApiError && err.code === 'VERSION_CONFLICT') this.board.reload();
  }
}
