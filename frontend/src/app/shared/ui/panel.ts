import { A11yModule } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Icon } from './icon';

let nextId = 0;

/**
 * Painel sobreposto: gaveta lateral (formulários) ou diálogo central (confirmações). Prende o foco, fecha com Esc
 * e no clique fora.
 */
@Component({
  selector: 'app-panel',
  imports: [A11yModule, Icon],
  template: `
    @if (open()) {
      <div class="backdrop" (click)="closed.emit()" aria-hidden="true"></div>
      <section
        class="panel"
        [class.dialog]="variant() === 'dialog'"
        [style.--panel-width.px]="width()"
        role="dialog"
        aria-modal="true"
        [attr.aria-labelledby]="titleId"
        cdkTrapFocus
        [cdkTrapFocusAutoCapture]="true"
        (keydown.escape)="closed.emit()"
      >
        <header class="panel-head">
          <div>
            <h2 [id]="titleId">{{ title() }}</h2>
            @if (subtitle()) {
              <p class="panel-sub">{{ subtitle() }}</p>
            }
          </div>
          <button type="button" class="btn ghost icon" (click)="closed.emit()" aria-label="Fechar">
            <app-icon name="x" />
          </button>
        </header>
        <div class="panel-body"><ng-content /></div>
        <footer class="panel-foot"><ng-content select="[panel-footer]" /></footer>
      </section>
    }
  `,
  styles: `
    .backdrop {
      position: fixed;
      inset: 0;
      z-index: 70;
      background: rgb(15 23 42 / 0.45);
      animation: fade 160ms ease;
    }
    .panel {
      position: fixed;
      z-index: 71;
      top: 0;
      right: 0;
      bottom: 0;
      width: min(var(--panel-width, 560px), 100vw);
      display: flex;
      flex-direction: column;
      background: var(--surface);
      box-shadow: var(--shadow-lg);
      animation: slide 220ms var(--ease);
    }
    .panel.dialog {
      top: 50%;
      left: 50%;
      right: auto;
      bottom: auto;
      translate: -50% -50%;
      max-height: calc(100svh - 32px);
      width: min(var(--panel-width, 440px), calc(100vw - 32px));
      border-radius: var(--radius);
      animation: pop 180ms var(--ease);
    }
    .panel-head {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 12px;
      padding: 20px 24px 16px;
      border-bottom: 1px solid var(--border);
    }
    .panel-head h2 {
      font-size: 18px;
      line-height: 26px;
      font-weight: 650;
    }
    .panel-sub {
      margin-top: 2px;
      color: var(--text-2);
    }
    .panel-body {
      flex: 1;
      /* sem isto o corpo cresce com o conteúdo e empurra o rodapé para fora da tela */
      min-height: 0;
      overflow-y: auto;
      padding: 20px 24px;
    }
    .panel-foot {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: flex-end;
      gap: 8px;
      padding: 14px 24px;
      border-top: 1px solid var(--border);
      background: var(--surface-2);
    }
    .panel.dialog .panel-foot {
      border-radius: 0 0 var(--radius) var(--radius);
    }
    .panel-foot:empty {
      display: none;
    }
    @keyframes fade {
      from {
        opacity: 0;
      }
    }
    @keyframes slide {
      from {
        transform: translateX(24px);
        opacity: 0;
      }
    }
    @keyframes pop {
      from {
        scale: 0.97;
        opacity: 0;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Panel {
  readonly open = input(false);
  readonly title = input.required<string>();
  readonly subtitle = input<string | null>(null);
  readonly variant = input<'drawer' | 'dialog'>('drawer');
  readonly width = input<number | null>(null);
  readonly closed = output<void>();
  protected readonly titleId = `panel-title-${nextId++}`;
}
