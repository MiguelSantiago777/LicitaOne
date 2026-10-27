import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

const PALETTE = [
  ['#dbeafe', '#1d4ed8'],
  ['#dcfce7', '#15803d'],
  ['#fef3c7', '#b45309'],
  ['#fce7f3', '#be185d'],
  ['#ede9fe', '#6d28d9'],
  ['#cffafe', '#0e7490'],
  ['#ffedd5', '#c2410c'],
  ['#e0e7ff', '#4338ca'],
];

/** Avatar com iniciais e uma cor estável por nome (a mesma pessoa ou órgão tem sempre a mesma cor). */
@Component({
  selector: 'app-avatar',
  template: `{{ initials() }}`,
  host: {
    '[style.width.px]': 'size()',
    '[style.height.px]': 'size()',
    '[style.font-size.px]': 'size() * 0.4',
    '[style.background]': 'colors()[0]',
    '[style.color]': 'colors()[1]',
    '[class.square]': 'square()',
    'aria-hidden': 'true',
  },
  styles: `
    :host {
      display: inline-grid;
      place-items: center;
      flex: none;
      border-radius: 50%;
      font-weight: 650;
      letter-spacing: 0.01em;
    }
    :host(.square) {
      border-radius: 8px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Avatar {
  readonly name = input.required<string>();
  readonly size = input(28);
  readonly square = input(false);

  protected readonly initials = computed(() => {
    const words = this.name()
      .replace(/^(Prefeitura Municipal de|Prefeitura de|Pref\.)\s+/i, '')
      .split(/\s+/)
      .filter((w) => w.length > 2 || /^[A-Z0-9]+$/.test(w));
    const first = words[0]?.[0] ?? '?';
    const last = words.length > 1 ? words[words.length - 1][0] : '';
    return (first + last).toUpperCase();
  });

  protected readonly colors = computed(() => {
    let hash = 0;
    for (const char of this.name()) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
    return PALETTE[hash % PALETTE.length];
  });
}
