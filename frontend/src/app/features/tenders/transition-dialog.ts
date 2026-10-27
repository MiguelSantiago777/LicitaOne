import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TendersService } from '../../api/generated/tenders/tenders.service';
import type { NextStatusResponse, TenderResponse } from '../../api/generated/model';
import { ApiError } from '../../core/http/api-error';
import { todayIso } from '../../shared/format';
import { Panel } from '../../shared/ui/panel';
import { ACTION_LABELS } from './tender-meta';

/** Confirma a mudança de situação, pedindo a data quando o novo estado exige. */
@Component({
  selector: 'app-transition-dialog',
  imports: [FormsModule, Panel],
  template: `
    <app-panel
      variant="dialog"
      [open]="!!target()"
      [title]="target() ? actionLabels[target()!.status] : ''"
      [subtitle]="tender()?.agency?.name ?? null"
      (closed)="closed.emit()"
    >
      @if (target(); as t) {
        <form id="transition-form" class="form" (ngSubmit)="confirm()">
          <p>
            A licitação passa de <strong>{{ tender()?.statusLabel }}</strong> para
            <strong>{{ t.label }}</strong
            >.
          </p>
          @if (t.requiredDate) {
            <div class="field">
              <label for="transition-date">{{ capitalize(t.requiredDate) }}</label>
              <input
                id="transition-date"
                type="date"
                class="input"
                name="date"
                [(ngModel)]="date"
                [max]="today"
                required
              />
            </div>
          }
          @if (error()) {
            <p class="error" role="alert">{{ error() }}</p>
          }
        </form>
      }
      <ng-container panel-footer>
        <button type="button" class="btn" (click)="closed.emit()">Cancelar</button>
        <button type="submit" form="transition-form" class="btn primary" [disabled]="saving()">
          {{ saving() ? 'Salvando…' : 'Confirmar' }}
        </button>
      </ng-container>
    </app-panel>
  `,
  styles: `
    .form {
      display: grid;
      gap: 16px;
    }
    .error {
      color: var(--red);
      font-weight: 500;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransitionDialog {
  private readonly api = inject(TendersService);

  readonly tender = input<TenderResponse | null>(null);
  readonly target = input<NextStatusResponse | null>(null);
  readonly closed = output<void>();
  readonly saved = output<TenderResponse>();

  protected readonly actionLabels = ACTION_LABELS;
  protected readonly today = todayIso();
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected date = todayIso();

  constructor() {
    effect(() => {
      if (this.target()) {
        this.date = todayIso();
        this.error.set(null);
      }
    });
  }

  protected capitalize(text: string): string {
    return text.charAt(0).toUpperCase() + text.slice(1);
  }

  protected confirm(): void {
    const t = this.tender();
    const target = this.target();
    if (!t || !target) return;
    if (target.requiredDate && !this.date) {
      this.error.set(`Informe a ${target.requiredDate}.`);
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.api
      .transitionTender(t.id, {
        version: t.version,
        target: target.status,
        date: target.requiredDate ? this.date : undefined,
      })
      .subscribe({
        next: (result) => {
          this.saving.set(false);
          this.saved.emit(result);
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível salvar.');
        },
      });
  }
}
