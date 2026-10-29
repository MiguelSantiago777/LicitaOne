import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import type { TaskEmailDraftResponse } from '../../api/generated/model';
import { Icon } from '../../shared/ui/icon';

/**
 * Rascunho de e-mail ao órgão gerado por um alerta (seção 5.4). O LicitaOne nunca envia: a pessoa copia ou abre no
 * cliente de e-mail, revisa e manda.
 */
@Component({
  selector: 'app-email-draft',
  imports: [Icon],
  template: `
    <section class="draft" aria-labelledby="draft-title">
      <header class="draft-head">
        <span class="draft-icon"><app-icon name="mail" [size]="18" /></span>
        <div>
          <h3 id="draft-title">Rascunho de e-mail ao órgão</h3>
          <p class="draft-sub">Revise antes de mandar. O LicitaOne não envia por você.</p>
        </div>
      </header>
      <dl class="draft-fields">
        <div>
          <dt>Para</dt>
          <dd>
            @if (draft().to) {
              {{ draft().to }}
            } @else {
              <span class="missing"
                >O órgão não tem contato com e-mail. Cadastre em Órgãos → Contatos ou preencha no
                seu e-mail.</span
              >
            }
          </dd>
        </div>
        <div>
          <dt>Assunto</dt>
          <dd>{{ draft().subject }}</dd>
        </div>
      </dl>
      <pre class="draft-body" tabindex="0" aria-label="Texto do e-mail">{{ draft().body }}</pre>
      <div class="draft-actions">
        <button type="button" class="btn sm" (click)="copy()">
          <app-icon name="copy" [size]="16" />Copiar texto
        </button>
        <a class="btn sm primary" [href]="mailto()"
          ><app-icon name="mail" [size]="16" />Abrir no e-mail</a
        >
        <span class="copied" role="status">{{ copied() }}</span>
      </div>
    </section>
  `,
  styles: `
    .draft {
      display: grid;
      gap: 12px;
      padding: 14px;
      border: 1px solid var(--border-strong);
      border-radius: 10px;
      background: var(--surface-2);
    }
    .draft-head {
      display: flex;
      gap: 10px;
      align-items: flex-start;
    }
    .draft-icon {
      display: grid;
      place-items: center;
      flex: none;
      width: 32px;
      height: 32px;
      border-radius: 8px;
      background: var(--blue-bg);
      color: var(--blue);
    }
    h3 {
      font-size: 14px;
      font-weight: 650;
    }
    .draft-sub {
      color: var(--text-2);
      font-size: 12px;
    }
    .draft-fields {
      display: grid;
      gap: 6px;
      margin: 0;
    }
    .draft-fields > div {
      display: grid;
      grid-template-columns: 64px minmax(0, 1fr);
      gap: 8px;
    }
    dt {
      color: var(--text-3);
      font-size: 12px;
      line-height: 20px;
    }
    dd {
      margin: 0;
      font-weight: 550;
      overflow-wrap: anywhere;
    }
    .missing {
      color: var(--amber);
      font-weight: 500;
    }
    .draft-body {
      max-height: 220px;
      margin: 0;
      padding: 12px;
      overflow: auto;
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
      background: var(--surface);
      font: inherit;
      line-height: 20px;
      white-space: pre-wrap;
      overflow-wrap: anywhere;
    }
    .draft-actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px;
    }
    .copied {
      color: var(--green);
      font-size: 12px;
      font-weight: 600;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmailDraft {
  readonly draft = input.required<TaskEmailDraftResponse>();

  protected readonly copied = signal('');

  /** mailto: com assunto e texto; destinatários separados por vírgula, como o cliente de e-mail espera. */
  protected readonly mailto = computed(() => {
    const d = this.draft();
    const to = (d.to ?? '')
      .split(',')
      .map((a) => a.trim())
      .filter(Boolean)
      .map(encodeURIComponent)
      .join(',');
    return `mailto:${to}?subject=${encodeURIComponent(d.subject)}&body=${encodeURIComponent(d.body)}`;
  });

  protected copy(): void {
    const d = this.draft();
    const text = `Assunto: ${d.subject}\n\n${d.body}`;
    navigator.clipboard.writeText(text).then(
      () => this.flash('Texto copiado.'),
      () => this.flash('Não foi possível copiar. Selecione o texto e copie.'),
    );
  }

  private flash(message: string): void {
    this.copied.set(message);
    setTimeout(() => this.copied.set(''), 4000);
  }
}
