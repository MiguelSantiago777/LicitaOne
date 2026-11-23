import { afterNextRender, DestroyRef, ElementRef, inject, signal } from '@angular/core';

/**
 * Largura do elemento em px, atualizada quando a tela muda: os gráficos desenham em px (e não num viewBox esticado)
 * para o texto do eixo não encolher no celular. Chame no construtor do componente.
 */
export function chartWidth(fallback = 640) {
  const width = signal(fallback);
  const host = inject<ElementRef<HTMLElement>>(ElementRef);
  const destroyRef = inject(DestroyRef);
  afterNextRender(() => {
    const el = host.nativeElement;
    width.set(Math.max(240, Math.floor(el.clientWidth)));
    const observer = new ResizeObserver(([entry]) =>
      width.set(Math.max(240, Math.floor(entry.contentRect.width))),
    );
    observer.observe(el);
    destroyRef.onDestroy(() => observer.disconnect());
  });
  return width;
}
