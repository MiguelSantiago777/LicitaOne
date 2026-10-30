import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Icon } from '../../shared/ui/icon';
import { Notices } from './notices';

@Component({
  selector: 'app-notice-region',
  imports: [Icon],
  template: `
    <div class="region" aria-live="polite" role="status">
      @for (notice of notices.items(); track notice.id) {
        <div class="notice" [class]="notice.tone" animate.leave="out">
          <app-icon class="tone-icon" [name]="icons[notice.tone]" [size]="18" />
          <p>{{ notice.message }}</p>
          <button
            type="button"
            class="btn ghost close"
            (click)="notices.dismiss(notice.id)"
            aria-label="Fechar aviso"
          >
            <app-icon name="x" />
          </button>
          <span class="timer" [style.animation-duration.ms]="notice.durationMs"></span>
        </div>
      }
    </div>
  `,
  styles: `
    .region {
      position: fixed;
      right: 16px;
      bottom: 16px;
      z-index: 80;
      display: grid;
      gap: 8px;
      width: min(380px, calc(100vw - 32px));
    }
    .notice {
      position: relative;
      overflow: hidden;
      display: flex;
      align-items: flex-start;
      gap: 10px;
      padding: 12px 8px 12px 14px;
      border-radius: var(--radius-sm);
      background: var(--nav-bg);
      color: #fff;
      box-shadow: var(--shadow-lg);
      animation: notice-in 420ms cubic-bezier(0.34, 1.4, 0.64, 1);
    }
    .notice.out {
      animation: notice-out 260ms cubic-bezier(0.4, 0, 1, 1) forwards;
    }
    .notice.warning {
      background: var(--amber-bg);
      color: var(--amber);
      border: 1px solid #fde68a;
    }
    .notice.error {
      background: var(--red-bg);
      color: var(--red);
      border: 1px solid #fecaca;
    }
    .tone-icon {
      flex: none;
      margin-top: 7px;
      color: #4ade80;
    }
    .warning .tone-icon,
    .error .tone-icon {
      color: inherit;
    }
    .notice p {
      flex: 1;
      padding-top: 6px;
    }
    .close {
      color: inherit;
      height: 32px;
      padding: 0 8px;
    }
    .close:hover {
      background: rgb(127 127 127 / 0.16);
    }
    /* barra que esvazia no tempo do aviso */
    .timer {
      position: absolute;
      left: 0;
      bottom: 0;
      width: 100%;
      height: 3px;
      background: currentColor;
      opacity: 0.35;
      transform-origin: left;
      animation: timer linear forwards;
    }
    @keyframes notice-in {
      from {
        opacity: 0;
        transform: translateX(110%);
      }
    }
    @keyframes notice-out {
      to {
        opacity: 0;
        transform: translateX(110%);
      }
    }
    @keyframes timer {
      to {
        transform: scaleX(0);
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NoticeRegion {
  protected readonly notices = inject(Notices);
  protected readonly icons = {
    info: 'circle-check',
    warning: 'circle-alert',
    error: 'circle-alert',
  };
}
