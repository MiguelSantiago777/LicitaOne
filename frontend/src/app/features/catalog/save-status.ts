import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Icon } from '../../shared/ui/icon';
import type { SaveState } from './autosave';

/** "Salvando…", "Salvo", "Não salvo": o estado do salvamento automático, lido pelo leitor de tela. */
@Component({
  selector: 'app-save-status',
  imports: [Icon],
  template: `
    <span class="save-state" role="status" [attr.data-state]="state()">
      @switch (state()) {
        @case ('pending') {
          Alterações não salvas…
        }
        @case ('saving') {
          Salvando…
        }
        @case ('saved') {
          <app-icon name="circle-check" [size]="14" />Salvo
        }
        @case ('error') {
          <app-icon name="circle-alert" [size]="14" />Não salvo
        }
        @case ('conflict') {
          <app-icon name="circle-alert" [size]="14" />Não salvo
        }
        @default {
          Salva sozinho enquanto você digita
        }
      }
    </span>
  `,
  styles: `
    .save-state {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
    .save-state[data-state='saved'] {
      color: var(--green);
    }
    .save-state[data-state='error'],
    .save-state[data-state='conflict'] {
      color: var(--red);
      font-weight: 550;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SaveStatus {
  readonly state = input.required<SaveState>();
}
