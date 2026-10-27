import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Marca as telas que ainda mostram dados de exemplo (até a fase que as torna reais). */
@Component({
  selector: 'app-demo-badge',
  template: `<span class="dot"></span>Dados de exemplo · real na Fase {{ phase() }}`,
  host: { title: 'Esta tela ainda não grava nada: mostra como vai funcionar.' },
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      height: 26px;
      padding: 0 10px;
      border-radius: 999px;
      background: var(--amber-bg);
      color: var(--amber);
      font-size: 12px;
      font-weight: 600;
      white-space: nowrap;
    }
    .dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: currentColor;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DemoBadge {
  readonly phase = input.required<number>();
}
