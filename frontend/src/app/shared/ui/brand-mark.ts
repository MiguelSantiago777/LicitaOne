import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** A marca: três barras decrescentes — o saldo de um contrato sendo pedido. */
@Component({
  selector: 'app-brand-mark',
  template: `
    <svg
      viewBox="0 0 32 32"
      [attr.width]="size()"
      [attr.height]="size()"
      aria-hidden="true"
      focusable="false"
    >
      <rect width="32" height="32" rx="8" fill="#2563eb" />
      <rect x="7" y="9" width="18" height="3.2" rx="1.6" fill="#fff" />
      <rect x="7" y="14.4" width="13" height="3.2" rx="1.6" fill="#fff" opacity=".85" />
      <rect x="7" y="19.8" width="7" height="3.2" rx="1.6" fill="#fff" opacity=".7" />
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
      flex: none;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BrandMark {
  readonly size = input(32);
}
