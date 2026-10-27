import { Injectable, signal } from '@angular/core';

export interface Notice {
  id: number;
  tone: 'info' | 'warning' | 'error';
  message: string;
}

/** Avisos curtos no canto da tela, lidos por leitores de tela (aria-live). */
@Injectable({ providedIn: 'root' })
export class Notices {
  private nextId = 1;
  readonly items = signal<Notice[]>([]);

  show(notice: Omit<Notice, 'id'>, durationMs = 8000): void {
    const id = this.nextId++;
    this.items.update((list) => [...list, { ...notice, id }]);
    setTimeout(() => this.dismiss(id), durationMs);
  }

  dismiss(id: number): void {
    this.items.update((list) => list.filter((n) => n.id !== id));
  }
}
