import { CurrencyPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { BoardsService } from '../../api/generated/boards/boards.service';
import type {
  BoardCardResponse,
  BoardCardTenderResponse,
  BoardResponse,
  TenderResponse,
} from '../../api/generated/model';
import { TendersService } from '../../api/generated/tenders/tenders.service';
import { UsersService } from '../../api/generated/users/users.service';
import { ApiError } from '../../core/http/api-error';
import { Icon } from '../../shared/ui/icon';
import { Panel } from '../../shared/ui/panel';

/**
 * Edita um card: título, observações, responsável, prazo e licitação ligada. O campo "Coluna" move o card sem
 * arrastar (para quem usa teclado ou celular).
 */
@Component({
  selector: 'app-card-dialog',
  imports: [CurrencyPipe, FormsModule, Icon, Panel],
  templateUrl: './card-dialog.html',
  styleUrl: './card-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CardDialog {
  private readonly api = inject(BoardsService);
  private readonly tendersApi = inject(TendersService);
  private readonly usersApi = inject(UsersService);

  readonly board = input<BoardResponse | null>(null);
  readonly card = input<BoardCardResponse | null>(null);
  readonly closed = output<void>();
  readonly saved = output<BoardResponse>();

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly confirmingDelete = signal(false);
  protected readonly tender = signal<BoardCardTenderResponse | null>(null);
  protected readonly tenderQuery$ = new Subject<string>();
  protected readonly tenderResults = signal<TenderResponse[]>([]);
  protected readonly owners = rxResource({ stream: () => this.usersApi.listUserOptions() });

  protected title = '';
  protected notes = '';
  protected ownerId = '';
  protected dueDate = '';
  protected columnId = '';
  protected tenderSearch = '';

  constructor() {
    effect(() => {
      const c = this.card();
      if (!c) return;
      this.error.set(null);
      this.confirmingDelete.set(false);
      this.title = c.title;
      this.notes = c.notes ?? '';
      this.ownerId = c.owner?.id ?? '';
      this.dueDate = c.dueDate ?? '';
      this.columnId = c.columnId;
      this.tender.set(c.tender ?? null);
      this.tenderSearch = '';
      this.tenderResults.set([]);
    });
    this.tenderQuery$
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((q) =>
          q.trim().length < 2 ? of(null) : this.tendersApi.searchTenders({ q: q.trim(), size: 8 }),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((page) => this.tenderResults.set(page?.items ?? []));
  }

  protected pickTender(t: TenderResponse): void {
    this.tender.set({
      id: t.id,
      agencyName: t.agency.name,
      identification: t.reference,
      totalValue: t.totalValue,
      status: t.status,
    });
    this.tenderSearch = '';
    this.tenderResults.set([]);
  }

  protected save(): void {
    const c = this.card();
    if (!c) return;
    if (!this.title.trim()) {
      this.error.set('Informe o título do card.');
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.api
      .updateBoardCard(c.id, {
        version: c.version,
        card: {
          title: this.title.trim(),
          notes: this.notes.trim() || undefined,
          ownerId: this.ownerId || undefined,
          dueDate: this.dueDate || undefined,
          tenderId: this.tender()?.id,
        },
      })
      .pipe(
        // Coluna trocada no diálogo: move para o fim da coluna escolhida depois de salvar o conteúdo
        switchMap((board) =>
          this.columnId === c.columnId
            ? of(board)
            : this.api.moveBoardCard(c.id, {
                columnId: this.columnId,
                position: board.columns.find((col) => col.id === this.columnId)?.cards.length ?? 0,
              }),
        ),
      )
      .subscribe({
        next: (board) => {
          this.saving.set(false);
          this.saved.emit(board);
        },
        error: (err: unknown) => this.fail(err),
      });
  }

  protected remove(): void {
    const c = this.card();
    if (!c) return;
    this.saving.set(true);
    this.api.deleteBoardCard(c.id).subscribe({
      next: (board) => {
        this.saving.set(false);
        this.saved.emit(board);
      },
      error: (err: unknown) => this.fail(err),
    });
  }

  private fail(err: unknown): void {
    this.saving.set(false);
    this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível salvar.');
  }
}
