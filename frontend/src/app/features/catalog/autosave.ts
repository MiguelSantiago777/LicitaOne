import { signal } from '@angular/core';
import type { Observable } from 'rxjs';
import { ApiError } from '../../core/http/api-error';

export type SaveState = 'idle' | 'pending' | 'saving' | 'saved' | 'error' | 'conflict';

const SAVE_DELAY_MS = 800;

/**
 * Salvamento automático das telas do cadastro, como nas propostas: 800 ms depois da última alteração manda o registro
 * inteiro com a versão. Um salvamento por vez; se a pessoa mexer durante um, salva de novo ao fim. Conflito de versão
 * para tudo até a pessoa recarregar.
 */
export class Autosave<T extends { version: number }> {
  readonly state = signal<SaveState>('idle');
  readonly error = signal<string | null>(null);

  private timer: ReturnType<typeof setTimeout> | null = null;
  private saving = false;
  private again = false;

  constructor(
    private readonly current: () => T | null,
    private readonly save: (version: number) => Observable<T>,
    private readonly saved: (value: T) => void,
  ) {}

  changed(): void {
    if (this.state() === 'conflict') return;
    this.state.set('pending');
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => {
      this.timer = null;
      this.flush();
    }, SAVE_DELAY_MS);
  }

  /** Salva já o que estiver esperando (ao sair da tela). */
  flushPending(): void {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
      this.flush();
    }
  }

  /** Nada esperando nem em andamento. */
  idle(): boolean {
    return !this.timer && !this.saving;
  }

  reset(): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
    this.again = false;
    this.state.set('idle');
    this.error.set(null);
  }

  private flush(): void {
    const value = this.current();
    if (!value) return;
    if (this.saving) {
      this.again = true;
      return;
    }
    this.saving = true;
    this.state.set('saving');
    this.save(value.version).subscribe({
      next: (v) => {
        this.saving = false;
        this.saved(v);
        this.error.set(null);
        if (this.again) {
          this.again = false;
          this.flush();
        } else if (!this.timer) {
          this.state.set('saved');
        }
      },
      error: (err: unknown) => {
        this.saving = false;
        this.again = false;
        if (err instanceof ApiError && err.code === 'VERSION_CONFLICT') {
          this.state.set('conflict');
          this.error.set(err.detail);
          return;
        }
        this.state.set('error');
        this.error.set(
          err instanceof ApiError
            ? (err.fieldErrors[0]?.message ?? err.detail)
            : 'Não foi possível salvar. Confira a conexão.',
        );
      },
    });
  }
}
