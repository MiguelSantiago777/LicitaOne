import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ExtractionsService } from '../../api/generated/extractions/extractions.service';
import { ApiError } from '../../core/http/api-error';
import { Icon } from '../../shared/ui/icon';

type Channel = 'EMAIL' | 'WHATSAPP';
type Tone = 'NORMAL' | 'CURTO' | 'FORMAL';

/** Só dígitos, com o 55 do Brasil quando o número veio sem o código do país (wa.me pede o número completo). */
export function whatsappNumber(phone: string | null | undefined): string | null {
  const digits = (phone ?? '').replace(/\D/g, '');
  if (digits.length < 10) return null;
  return digits.length <= 11 ? `55${digits}` : digits;
}

/**
 * "Escrever com IA" (Fase 9): o follow-up ao órgão com o contexto do contrato (saldo, itens, último pedido) e dos
 * últimos contatos registrados. O texto fica editável aqui; o LicitaOne não envia nada: a pessoa copia ou abre no
 * e-mail ou no WhatsApp.
 */
@Component({
  selector: 'app-follow-up-writer',
  imports: [FormsModule, Icon],
  template: `
    @if (settings.value()?.enabled) {
      <section class="ai-fill" aria-labelledby="follow-up-title">
        <div class="ai-head">
          <app-icon name="sparkles" [size]="18" />
          <div>
            <h3 id="follow-up-title">Escrever com IA</h3>
            <p>
              A IA escreve o follow-up citando o contrato, o saldo e os últimos contatos. Revise
              antes de mandar; o LicitaOne não envia por você.
            </p>
          </div>
        </div>
        <div class="modes" role="radiogroup" aria-label="Canal do follow-up">
          <button
            type="button"
            role="radio"
            class="mode"
            [attr.aria-checked]="channel() === 'EMAIL'"
            (click)="channel.set('EMAIL')"
            [disabled]="busy()"
          >
            <app-icon name="mail" [size]="14" />E-mail
          </button>
          <button
            type="button"
            role="radio"
            class="mode"
            [attr.aria-checked]="channel() === 'WHATSAPP'"
            (click)="channel.set('WHATSAPP')"
            [disabled]="busy()"
          >
            <app-icon name="handshake" [size]="14" />WhatsApp
          </button>
        </div>
        <div class="ai-actions">
          @if (body()) {
            <button type="button" class="btn sm" (click)="write('CURTO')" [disabled]="busy()">
              Mais curto
            </button>
            <button type="button" class="btn sm" (click)="write('FORMAL')" [disabled]="busy()">
              Mais formal
            </button>
          }
          <button
            type="button"
            class="btn sm primary"
            (click)="write('NORMAL')"
            [disabled]="busy()"
            [attr.aria-busy]="busy() || null"
          >
            <app-icon name="sparkles" [size]="14" />{{
              busy() ? 'Escrevendo…' : body() ? 'Escrever de novo' : 'Escrever com IA'
            }}
          </button>
        </div>
        <div class="ai-status" role="status" aria-live="polite">
          @if (busy()) {
            <span class="spinner" aria-hidden="true"></span>
            Escrevendo. Leva alguns segundos.
          }
        </div>
        @if (error(); as message) {
          <div class="alert" role="alert">{{ message }}</div>
        }
        @if (body()) {
          @if (warnings().length) {
            <ul class="ai-warnings">
              @for (w of warnings(); track $index) {
                <li><app-icon name="alert" [size]="14" />{{ w }}</li>
              }
            </ul>
          }
          @if (written() === 'EMAIL') {
            <p class="meta">
              <span class="meta-label">Para</span>
              {{ to() ?? 'O órgão não tem contato com e-mail. Cadastre em Órgãos → Contatos.' }}
            </p>
            <div class="field">
              <label for="follow-up-subject">Assunto</label>
              <input
                id="follow-up-subject"
                class="input"
                name="followUpSubject"
                [ngModel]="subject()"
                (ngModelChange)="subject.set($event)"
                maxlength="200"
              />
            </div>
          }
          <div class="field">
            <label for="follow-up-body">{{
              written() === 'EMAIL' ? 'Texto do e-mail' : 'Mensagem de WhatsApp'
            }}</label>
            <textarea
              id="follow-up-body"
              class="input textarea"
              name="followUpBody"
              rows="8"
              maxlength="4000"
              [ngModel]="body()"
              (ngModelChange)="body.set($event)"
            ></textarea>
          </div>
          <div class="ai-actions">
            <span class="copied" role="status">{{ copied() }}</span>
            <button type="button" class="btn sm" (click)="copy()">
              <app-icon name="copy" [size]="14" />Copiar texto
            </button>
            @if (written() === 'EMAIL') {
              <a class="btn sm primary" [href]="mailto()"
                ><app-icon name="mail" [size]="14" />Abrir no e-mail</a
              >
            } @else {
              <a class="btn sm primary" [href]="whatsapp()" target="_blank" rel="noopener"
                ><app-icon name="arrow-up-right" [size]="14" />Abrir no WhatsApp</a
              >
            }
          </div>
          @if (written() === 'WHATSAPP' && !phone()) {
            <p class="hint">
              O órgão não tem telefone cadastrado: o WhatsApp abre para você escolher a conversa.
            </p>
          }
        }
      </section>
    }
  `,
  styleUrl: '../price-records/document-ai-fill.scss',
  styles: `
    .modes {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
    }
    .mode {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      height: 30px;
      padding: 0 12px;
      border: 1px solid var(--border);
      border-radius: 999px;
      background: var(--surface);
      color: var(--text-2);
      font: inherit;
      font-size: 13px;
      font-weight: 550;
      cursor: pointer;
    }
    .mode[aria-checked='true'] {
      border-color: var(--violet);
      color: var(--violet);
    }
    .mode:focus-visible {
      outline: 2px solid var(--violet);
      outline-offset: 2px;
    }
    .ai-actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: flex-end;
      gap: 8px;
    }
    .textarea {
      height: auto;
      padding: 10px 12px;
      resize: vertical;
      line-height: 20px;
      background: var(--surface);
    }
    .field .input {
      background: var(--surface);
    }
    .meta {
      font-size: 13px;
      overflow-wrap: anywhere;
    }
    .meta-label {
      margin-right: 6px;
      color: var(--text-3);
    }
    .copied {
      margin-right: auto;
      color: var(--green);
      font-size: 12px;
      font-weight: 550;
    }
    .hint {
      color: var(--text-3);
      font-size: 12px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FollowUpWriter {
  private readonly api = inject(ExtractionsService);

  readonly tenderId = input.required<string>();
  /** Contrato de que se fala; sem ele, a IA usa todos os contratos abertos da licitação. */
  readonly contractId = input<string | null>(null);

  protected readonly settings = rxResource({ stream: () => this.api.getExtractionSettings() });
  protected readonly channel = signal<Channel>('EMAIL');
  /** Canal do texto que está na tela (a pessoa pode trocar o canal antes de escrever de novo). */
  protected readonly written = signal<Channel>('EMAIL');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly subject = signal('');
  protected readonly body = signal('');
  protected readonly to = signal<string | null>(null);
  protected readonly phone = signal<string | null>(null);
  protected readonly warnings = signal<string[]>([]);
  protected readonly copied = signal('');

  protected readonly mailto = computed(
    () =>
      `mailto:${encodeURIComponent(this.to() ?? '').replace(/%2C/g, ',')}` +
      `?subject=${encodeURIComponent(this.subject())}&body=${encodeURIComponent(this.body())}`,
  );
  protected readonly whatsapp = computed(() => {
    const number = whatsappNumber(this.phone());
    const text = encodeURIComponent(this.body());
    return number ? `https://wa.me/${number}?text=${text}` : `https://wa.me/?text=${text}`;
  });

  protected write(tone: Tone): void {
    const channel = this.channel();
    this.error.set(null);
    this.copied.set('');
    this.busy.set(true);
    this.api
      .writeFollowUpDraft(this.tenderId(), {
        channel,
        tone,
        contractId: this.contractId() ?? undefined,
      })
      .subscribe({
        next: (view) => {
          this.busy.set(false);
          this.written.set(channel);
          this.subject.set(view.draft.subject ?? '');
          this.body.set(view.draft.body ?? '');
          this.warnings.set(view.draft.warnings ?? []);
          this.to.set(view.to ?? null);
          this.phone.set(view.phone ?? null);
        },
        error: (err: unknown) => {
          this.busy.set(false);
          this.error.set(
            err instanceof ApiError ? err.detail : 'A IA não conseguiu escrever agora.',
          );
        },
      });
  }

  protected copy(): void {
    const text = this.written() === 'EMAIL' ? `${this.subject()}\n\n${this.body()}` : this.body();
    navigator.clipboard.writeText(text).then(
      () => this.copied.set('Texto copiado.'),
      () => this.copied.set('Não deu para copiar. Selecione o texto e copie.'),
    );
  }
}
