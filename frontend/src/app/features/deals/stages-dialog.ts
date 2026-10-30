import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { DealsService } from '../../api/generated/deals/deals.service';
import type {
  NewStageRequestKind,
  PipelineResponse,
  PipelineStageResponse,
} from '../../api/generated/model';
import { ApiError } from '../../core/http/api-error';
import { Icon } from '../../shared/ui/icon';
import { Panel } from '../../shared/ui/panel';

const KINDS: { value: NewStageRequestKind; label: string }[] = [
  { value: 'ABERTA', label: 'Em andamento' },
  { value: 'GANHA', label: 'Fechado ganho' },
  { value: 'PERDIDA', label: 'Fechado perdido' },
];

/**
 * Etapas do funil (só o administrador): renomear, reordenar, criar e excluir. Etapa com negociação não sai, e o funil
 * sempre mantém uma etapa de cada tipo.
 */
@Component({
  selector: 'app-stages-dialog',
  imports: [FormsModule, Icon, Panel],
  template: `
    <app-panel
      [open]="open()"
      title="Etapas do funil"
      subtitle="Valem para toda a equipe."
      [width]="520"
      (closed)="closed.emit()"
    >
      @if (pipeline(); as p) {
        @if (error(); as message) {
          <div class="alert" role="alert">{{ message }}</div>
        }
        <ol class="stages">
          @for (s of p.stages; track s.id; let first = $first; let last = $last) {
            <li>
              @if (renaming() === s.id) {
                <form class="inline" (ngSubmit)="rename(p, s)">
                  <input
                    class="input"
                    name="stageName"
                    [(ngModel)]="name"
                    maxlength="60"
                    [attr.aria-label]="'Novo nome para ' + s.name"
                    (keydown.escape)="renaming.set(null)"
                  />
                  <button
                    type="submit"
                    class="btn sm primary"
                    [disabled]="busy()"
                    [attr.aria-busy]="busy() || null"
                  >
                    Salvar
                  </button>
                </form>
              } @else if (confirming() === s.id) {
                <span class="confirm">Excluir "{{ s.name }}"?</span>
                <button type="button" class="btn sm danger" (click)="remove(p, s)">Excluir</button>
                <button type="button" class="btn sm" (click)="confirming.set(null)">Manter</button>
              } @else {
                <div class="stage-name">
                  <span class="cell-main">{{ s.name }}</span>
                  <span class="cell-sub">{{ kindLabel(s) }}</span>
                </div>
                <button
                  type="button"
                  class="btn ghost icon sm"
                  [disabled]="first || busy()"
                  (click)="move(p, s, -1)"
                  [attr.aria-label]="'Mover ' + s.name + ' para a esquerda'"
                >
                  <app-icon name="chevron-right" [size]="16" class="up" />
                </button>
                <button
                  type="button"
                  class="btn ghost icon sm"
                  [disabled]="last || busy()"
                  (click)="move(p, s, 1)"
                  [attr.aria-label]="'Mover ' + s.name + ' para a direita'"
                >
                  <app-icon name="chevron-right" [size]="16" class="down" />
                </button>
                <button type="button" class="btn ghost sm" (click)="startRename(s)">
                  Renomear
                </button>
                <button
                  type="button"
                  class="btn ghost sm danger-text"
                  (click)="confirming.set(s.id)"
                >
                  Excluir
                </button>
              }
            </li>
          }
        </ol>
        <form class="add" (ngSubmit)="add(p)">
          <div class="field grow">
            <label for="new-stage">Nova etapa</label>
            <input
              id="new-stage"
              class="input"
              name="newStage"
              [(ngModel)]="newName"
              maxlength="60"
              placeholder="Ex.: Negociação de preço"
            />
          </div>
          <div class="field">
            <label for="new-stage-kind">Tipo</label>
            <select id="new-stage-kind" class="input" name="newKind" [(ngModel)]="newKind">
              @for (k of kinds; track k.value) {
                <option [value]="k.value">{{ k.label }}</option>
              }
            </select>
          </div>
          <button
            type="submit"
            class="btn primary"
            [disabled]="busy() || !newName.trim()"
            [attr.aria-busy]="busy() || null"
          >
            <app-icon name="plus" [size]="16" />Adicionar
          </button>
        </form>
      }
    </app-panel>
  `,
  styles: `
    .alert {
      margin-bottom: 12px;
      padding: 10px 14px;
      border-radius: var(--radius-sm);
      background: var(--red-bg);
      color: var(--red);
      font-weight: 500;
    }
    .stages {
      display: grid;
      gap: 6px;
      margin: 0 0 20px;
      padding: 0;
      list-style: none;
    }
    .stages li {
      display: flex;
      align-items: center;
      gap: 4px;
      min-height: 52px;
      padding: 6px 8px 6px 12px;
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
    }
    .stage-name {
      display: grid;
      flex: 1;
      min-width: 0;
    }
    .inline {
      display: flex;
      flex: 1;
      gap: 8px;
    }
    .inline .input {
      flex: 1;
    }
    .confirm {
      flex: 1;
      font-weight: 550;
    }
    .up {
      transform: rotate(-90deg);
    }
    .down {
      transform: rotate(90deg);
    }
    .danger-text {
      color: var(--red);
    }
    .btn.danger {
      background: var(--red);
      border-color: var(--red);
      color: #fff;
    }
    .add {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-end;
      gap: 8px;
    }
    .grow {
      flex: 1;
      min-width: 180px;
    }
    @media (max-width: 560px) {
      .stages li {
        flex-wrap: wrap;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StagesDialog {
  private readonly api = inject(DealsService);

  readonly open = input(false);
  readonly pipeline = input<PipelineResponse | null>(null);
  readonly closed = output<void>();
  /** Estrutura nova do funil (sem filtro); a página recarrega com o filtro dela. */
  readonly changed = output<void>();

  protected readonly kinds = KINDS;
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly renaming = signal<string | null>(null);
  protected readonly confirming = signal<string | null>(null);
  protected name = '';
  protected newName = '';
  protected newKind: NewStageRequestKind = 'ABERTA';

  protected kindLabel(s: PipelineStageResponse): string {
    return KINDS.find((k) => k.value === s.kind)?.label ?? s.kind;
  }

  protected startRename(s: PipelineStageResponse): void {
    this.name = s.name;
    this.confirming.set(null);
    this.renaming.set(s.id);
  }

  protected rename(p: PipelineResponse, s: PipelineStageResponse): void {
    if (!this.name.trim() || this.name.trim() === s.name) {
      this.renaming.set(null);
      return;
    }
    this.run(this.api.renameDealStage(s.id, { version: p.version, name: this.name.trim() }), () =>
      this.renaming.set(null),
    );
  }

  protected move(p: PipelineResponse, s: PipelineStageResponse, delta: number): void {
    this.run(this.api.moveDealStage(s.id, { version: p.version, position: s.position + delta }));
  }

  protected remove(p: PipelineResponse, s: PipelineStageResponse): void {
    this.run(this.api.removeDealStage(s.id, { version: p.version }), () =>
      this.confirming.set(null),
    );
  }

  protected add(p: PipelineResponse): void {
    if (!this.newName.trim()) return;
    this.run(
      this.api.addDealStage({ version: p.version, name: this.newName.trim(), kind: this.newKind }),
      () => {
        this.newName = '';
        this.newKind = 'ABERTA';
      },
    );
  }

  private run(request: Observable<PipelineResponse>, after?: () => void): void {
    this.busy.set(true);
    this.error.set(null);
    request.subscribe({
      next: () => {
        this.busy.set(false);
        after?.();
        this.changed.emit();
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.confirming.set(null);
        this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível salvar.');
        if (err instanceof ApiError && err.code === 'VERSION_CONFLICT') this.changed.emit();
      },
    });
  }
}
