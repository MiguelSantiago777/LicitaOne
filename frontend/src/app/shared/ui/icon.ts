import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Ícones de traço (16px, 1.5px), desenhados para o ShemaCRM. Sempre decorativos: o rótulo vem do texto ao lado. */
const PATHS: Record<string, string> = {
  dashboard: 'M3 3h7v9H3zM14 3h7v5h-7zM14 12h7v9h-7zM3 16h7v5H3z',
  tenders: 'M7 3h7l5 5v13H7zM14 3v5h5M10 13h6M10 17h6',
  records: 'M5 4h14v16H5zM9 4v16M12 9h4M12 13h4',
  deals: 'M4 5h4v14H4zM10 5h4v9h-4zM16 5h4v5h-4z',
  tasks: 'M4 6l2 2 3-3M12 7h8M4 13l2 2 3-3M12 14h8M12 20h8',
  imports: 'M12 3v12M7 10l5 5 5-5M4 19h16',
  logout: 'M15 4h4v16h-4M10 8l-4 4 4 4M6 12h10',
  menu: 'M4 7h16M4 12h16M4 17h16',
  x: 'M6 6l12 12M18 6L6 18',
  search: 'M11 4a7 7 0 1 1 0 14 7 7 0 0 1 0-14zM20 20l-4-4',
  plus: 'M12 5v14M5 12h14',
  alert: 'M12 4l9 16H3zM12 10v4M12 17v.5',
};

@Component({
  selector: 'app-icon',
  template: `
    <svg
      viewBox="0 0 24 24"
      [attr.width]="size()"
      [attr.height]="size()"
      aria-hidden="true"
      focusable="false"
    >
      <path [attr.d]="path()" />
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
      flex: none;
    }
    svg {
      fill: none;
      stroke: currentColor;
      stroke-width: 1.5;
      stroke-linecap: round;
      stroke-linejoin: round;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Icon {
  readonly name = input.required<string>();
  readonly size = input(16);
  protected readonly path = computed(() => PATHS[this.name()] ?? '');
}
