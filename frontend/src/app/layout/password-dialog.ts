import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { UsersService } from '../api/generated/users/users.service';
import { ApiError } from '../core/http/api-error';
import { Notices } from '../core/notices/notices';
import { Panel } from '../shared/ui/panel';

@Component({
  selector: 'app-password-dialog',
  imports: [FormsModule, Panel],
  template: `
    <app-panel
      variant="dialog"
      [open]="open()"
      title="Alterar minha senha"
      (closed)="closed.emit()"
    >
      <form id="password-form" class="form" (ngSubmit)="save()">
        <div class="field">
          <label for="p-current">Senha atual</label>
          <input
            id="p-current"
            class="input"
            type="password"
            name="current"
            autocomplete="current-password"
            [(ngModel)]="current"
            required
          />
        </div>
        <div class="field">
          <label for="p-new">Nova senha</label>
          <input
            id="p-new"
            class="input"
            type="password"
            name="next"
            autocomplete="new-password"
            [(ngModel)]="next"
            aria-describedby="p-hint"
            required
          />
          <p id="p-hint" class="hint">Pelo menos 10 caracteres.</p>
        </div>
        @if (error()) {
          <p class="error" role="alert">{{ error() }}</p>
        }
      </form>
      <ng-container panel-footer>
        <button type="button" class="btn" (click)="closed.emit()">Cancelar</button>
        <button type="submit" form="password-form" class="btn primary" [disabled]="saving()">
          {{ saving() ? 'Salvando…' : 'Alterar senha' }}
        </button>
      </ng-container>
    </app-panel>
  `,
  styles: `
    .form {
      display: grid;
      gap: 14px;
    }
    .hint {
      color: var(--text-3);
      font-size: 12px;
    }
    .error {
      color: var(--red);
      font-weight: 500;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordDialog {
  private readonly api = inject(UsersService);
  private readonly notices = inject(Notices);
  readonly open = input(false);
  readonly closed = output<void>();
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected current = '';
  protected next = '';

  protected save(): void {
    this.saving.set(true);
    this.error.set(null);
    this.api
      .changeOwnPassword({ currentPassword: this.current, newPassword: this.next })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.current = '';
          this.next = '';
          this.notices.show({ tone: 'info', message: 'Senha alterada.' });
          this.closed.emit();
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.error.set(
            err instanceof ApiError ? err.detail : 'Não foi possível alterar a senha.',
          );
        },
      });
  }
}
