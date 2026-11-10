import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subscription, filter, interval, switchMap, take, takeWhile, timeout } from 'rxjs';
import { AssistantService } from '../../api/generated/assistant/assistant.service';
import type {
  AssistantAnswerResponse,
  AssistantItemResponse,
  AssistantSettingsResponse,
} from '../../api/generated/model';
import { ApiError } from '../../core/http/api-error';
import { Icon } from '../../shared/ui/icon';
import { Panel } from '../../shared/ui/panel';

/** Uma pergunta da conversa aberta e o que aconteceu com ela. */
interface Turn {
  question: string;
  steps: string[];
  answer: AssistantAnswerResponse | null;
  error: string | null;
  showQueries: boolean;
}

const TYPE_LABEL: Record<string, string> = {
  LICITACAO: 'Licitação',
  CONTRATO: 'Contrato',
  ATA: 'Ata',
  ORGAO: 'Órgão',
  TAREFA: 'Tarefa',
  NEGOCIACAO: 'Negociação',
};

/** Perguntas que voltam como contexto (o servidor também corta em 10). */
const HISTORY = 10;

/**
 * "Pergunte ao LicitaOne" (Fase 11): perguntas sobre a carteira, respondidas com consultas de leitura. A conversa
 * vive só enquanto o painel está aberto (nada é guardado). A pergunta roda no servidor e a tela acompanha os passos a
 * cada segundo. A lista clicável só traz itens que as consultas devolveram.
 */
@Component({
  selector: 'app-assistant-panel',
  imports: [FormsModule, Icon, Panel],
  template: `
    <app-panel
      [open]="open()"
      title="Pergunte ao LicitaOne"
      subtitle="Respostas com os dados da carteira. O assistente só consulta."
      [width]="560"
      (closed)="close()"
    >
      <div class="chat">
        @if (!turns().length) {
          <div class="intro">
            <p>
              Pergunte sobre licitações, contratos e saldos, atas e caronas, tarefas ou o funil. A
              resposta traz a lista dos itens, para você abrir cada um.
            </p>
            @if (settings()?.examples?.length) {
              <p class="examples-title">Exemplos</p>
              <ul class="examples">
                @for (e of settings()!.examples; track e) {
                  <li>
                    <button type="button" class="example" (click)="ask(e)">{{ e }}</button>
                  </li>
                }
              </ul>
            }
          </div>
        }

        <ol class="turns" aria-label="Conversa">
          @for (t of turns(); track $index) {
            <li class="turn">
              <p class="question"><span class="visually-hidden">Você: </span>{{ t.question }}</p>
              @if (t.answer; as a) {
                <div class="answer">
                  <p class="text">{{ a.text }}</p>
                  @if (a.warnings.length) {
                    <ul class="warnings">
                      @for (w of a.warnings; track $index) {
                        <li><app-icon name="alert" [size]="14" />{{ w }}</li>
                      }
                    </ul>
                  }
                  @if (a.items.length) {
                    <ul class="items" [attr.aria-label]="'Itens da resposta: ' + a.items.length">
                      @for (item of a.items; track item.id) {
                        <li>
                          <button type="button" class="item" (click)="openItem(item)">
                            <span class="item-type">{{ typeLabel(item.type) }}</span>
                            <span class="item-main">{{ item.label }}</span>
                            @if (item.detail) {
                              <span class="item-sub">{{ item.detail }}</span>
                            }
                          </button>
                        </li>
                      }
                    </ul>
                  }
                  @if (a.queries.length) {
                    <button
                      type="button"
                      class="link queries-toggle"
                      [attr.aria-expanded]="t.showQueries"
                      (click)="toggleQueries($index)"
                    >
                      {{ t.showQueries ? 'Esconder as consultas' : 'Ver as consultas feitas' }}
                    </button>
                    @if (t.showQueries) {
                      <ul class="queries">
                        @for (q of a.queries; track $index) {
                          <li>
                            <strong>{{ q.label }}</strong> · {{ q.filters }} · {{ q.rows }}
                            {{ q.rows === 1 ? 'resultado' : 'resultados' }}
                          </li>
                        }
                      </ul>
                    }
                  }
                </div>
              } @else if (t.error) {
                <div class="alert" role="alert">{{ t.error }}</div>
              } @else {
                <div class="thinking" role="status" aria-live="polite">
                  <span class="spinner" aria-hidden="true"></span>
                  {{ t.steps.length ? t.steps[t.steps.length - 1] : 'Enviando…' }}
                </div>
              }
            </li>
          }
        </ol>
      </div>
      <form panel-footer class="ask" (submit)="submit($event)">
        <label for="assistant-question" class="visually-hidden">Sua pergunta</label>
        <textarea
          #questionBox
          id="assistant-question"
          class="input textarea"
          name="question"
          rows="2"
          maxlength="1000"
          placeholder="Ex.: quais contratos têm mais saldo e não pedem há 60 dias?"
          [(ngModel)]="draft"
          (keydown.enter)="enter($event)"
          [disabled]="busy()"
        ></textarea>
        <div class="ask-row">
          <span class="usage">
            @if (settings(); as s) {
              {{ s.usedToday }} de {{ s.dailyLimit }} perguntas hoje
            }
          </span>
          @if (turns().length) {
            <button type="button" class="btn sm ghost" (click)="reset()" [disabled]="busy()">
              Nova conversa
            </button>
          }
          <button
            type="submit"
            class="btn sm primary"
            [disabled]="busy() || !draft.trim()"
            [attr.aria-busy]="busy() || null"
          >
            <app-icon name="sparkles" [size]="14" />Perguntar
          </button>
        </div>
      </form>
    </app-panel>
  `,
  styleUrl: './assistant-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssistantPanel {
  private readonly api = inject(AssistantService);
  private readonly router = inject(Router);

  readonly open = input(false);
  readonly closed = output<void>();

  private readonly questionBox = viewChild<ElementRef<HTMLTextAreaElement>>('questionBox');

  protected readonly settings = signal<AssistantSettingsResponse | null>(null);
  protected readonly turns = signal<Turn[]>([]);
  protected readonly busy = signal(false);
  protected draft = '';
  private polling: Subscription | null = null;

  constructor() {
    effect(() => {
      if (!this.open()) return;
      this.loadSettings();
      setTimeout(() => this.questionBox()?.nativeElement.focus());
    });
    inject(DestroyRef).onDestroy(() => this.polling?.unsubscribe());
  }

  protected typeLabel(type: string): string {
    return TYPE_LABEL[type] ?? type;
  }

  protected enter(event: Event): void {
    const key = event as KeyboardEvent;
    if (key.shiftKey) return;
    event.preventDefault();
    this.ask(this.draft);
  }

  protected submit(event: Event): void {
    event.preventDefault();
    this.ask(this.draft);
  }

  protected ask(text: string): void {
    const question = text.trim();
    if (!question || this.busy()) return;
    const history = this.turns()
      .filter((t) => t.answer)
      .slice(-HISTORY)
      .map((t) => ({ question: t.question, answer: t.answer!.text }));
    const index = this.turns().length;
    this.turns.update((list) => [
      ...list,
      { question, steps: [], answer: null, error: null, showQueries: false },
    ]);
    this.draft = '';
    this.busy.set(true);
    this.api.askAssistant({ question, history }).subscribe({
      next: (started) => {
        this.patch(index, { steps: started.steps });
        this.follow(index, started.id);
      },
      error: (err: unknown) => this.fail(index, err),
    });
  }

  protected openItem(item: AssistantItemResponse): void {
    const query = item.query as Record<string, string> | undefined;
    void this.router.navigate([item.path], { queryParams: query ?? {} });
    this.close();
  }

  protected toggleQueries(index: number): void {
    this.turns.update((list) =>
      list.map((t, i) => (i === index ? { ...t, showQueries: !t.showQueries } : t)),
    );
  }

  protected reset(): void {
    this.polling?.unsubscribe();
    this.turns.set([]);
    this.busy.set(false);
    this.draft = '';
  }

  /** Fechar apaga a conversa: nada fica guardado (decisão de 04/11/2026). */
  protected close(): void {
    this.reset();
    this.closed.emit();
  }

  private follow(index: number, id: string): void {
    this.polling?.unsubscribe();
    this.polling = interval(1000)
      .pipe(
        switchMap(() => this.api.assistantQuestion(id)),
        takeWhile((q) => q.status === 'RUNNING', true),
        filter((q) => q.status !== 'RUNNING' || q.steps.length > 0),
        timeout(180_000),
      )
      .subscribe({
        next: (q) => {
          if (q.status === 'RUNNING') {
            this.patch(index, { steps: q.steps });
            return;
          }
          this.busy.set(false);
          if (q.status === 'DONE' && q.answer) {
            this.patch(index, { steps: q.steps, answer: q.answer });
          } else {
            this.patch(index, { error: q.error || 'Não consegui responder agora. Tente de novo.' });
          }
          this.loadSettings();
        },
        error: (err: unknown) => this.fail(index, err),
      });
  }

  private fail(index: number, err: unknown): void {
    this.busy.set(false);
    this.patch(index, {
      error:
        err instanceof ApiError
          ? err.detail
          : 'A resposta demorou demais ou a conexão caiu. Tente de novo.',
    });
    this.loadSettings();
  }

  private patch(index: number, change: Partial<Turn>): void {
    this.turns.update((list) => list.map((t, i) => (i === index ? { ...t, ...change } : t)));
  }

  private loadSettings(): void {
    this.api
      .assistantSettings()
      .pipe(take(1))
      .subscribe({ next: (s) => this.settings.set(s), error: () => undefined });
  }
}
