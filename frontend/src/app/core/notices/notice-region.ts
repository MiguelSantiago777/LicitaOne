import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Icon } from '../../shared/ui/icon';
import { Notices } from './notices';

@Component({
  selector: 'app-notice-region',
  imports: [Icon],
  template: `
    <div class="region" aria-live="polite" role="status">
      @for (notice of notices.items(); track notice.id) {
        <div class="notice" [class]="notice.tone">
          <p>{{ notice.message }}</p>
          <button
            type="button"
            class="btn ghost close"
            (click)="notices.dismiss(notice.id)"
            aria-label="Fechar aviso"
          >
            <app-icon name="x" />
          </button>
        </div>
      }
    </div>
  `,
  styles: `
    .region {
      position: fixed;
      right: 16px;
      bottom: 16px;
      z-index: 50;
      display: grid;
      gap: 8px;
      width: min(380px, calc(100vw - 32px));
    }
    .notice {
      display: flex;
      align-items: flex-start;
      gap: 8px;
      padding: 12px 8px 12px 14px;
      border-radius: var(--radius-card);
      background: var(--primary);
      color: var(--primary-ink);
      box-shadow: 0 8px 24px rgb(0 0 0 / 0.16);
    }
    .notice p {
      flex: 1;
      padding-top: 6px;
    }
    .notice.warning {
      background: var(--pill-warning-bg);
      color: var(--pill-warning-ink);
      border: 1px solid #fde68a;
      box-shadow: none;
    }
    .close {
      color: inherit;
      height: 32px;
      padding: 0 8px;
    }
    .close:hover {
      background: rgb(127 127 127 / 0.16);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NoticeRegion {
  protected readonly notices = inject(Notices);
}
