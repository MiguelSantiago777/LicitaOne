import { Directive, ElementRef, effect, inject, input } from '@angular/core';

const formats = {
  int: new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 }),
  brl: new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    maximumFractionDigits: 0,
  }),
};

/**
 * Número que conta até o valor (indicadores do painel). Sem valor mostra "—"; com "reduzir movimento" ligado (ou
 * fora do navegador) mostra o valor direto. O elemento deve vir vazio no template: o texto é escrito aqui.
 */
@Directive({ selector: '[appCountUp]' })
export class CountUp {
  readonly appCountUp = input.required<number | null | undefined>();
  readonly countFormat = input<keyof typeof formats>('int');

  constructor() {
    const el = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    let shown = 0;

    effect((onCleanup) => {
      const target = this.appCountUp();
      const format = formats[this.countFormat()];
      if (target == null) {
        el.textContent = '—';
        return;
      }
      const animate =
        globalThis.matchMedia?.('(prefers-reduced-motion: no-preference)').matches &&
        typeof requestAnimationFrame === 'function';
      if (!animate || shown === target) {
        shown = target;
        el.textContent = format.format(target);
        return;
      }
      const from = shown;
      const duration = 1100;
      let start: number | null = null;
      let frame = 0;
      const step = (now: number) => {
        start ??= now;
        const t = Math.min(1, (now - start) / duration);
        shown = t < 1 ? from + (target - from) * (1 - Math.pow(1 - t, 4)) : target;
        el.textContent = format.format(shown);
        if (t < 1) frame = requestAnimationFrame(step);
      };
      frame = requestAnimationFrame(step);
      onCleanup(() => cancelAnimationFrame(frame));
    });
  }
}
