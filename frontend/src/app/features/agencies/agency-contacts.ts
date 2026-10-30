import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AgenciesService } from '../../api/generated/agencies/agencies.service';
import type { ContactResponse } from '../../api/generated/model';
import { ApiError } from '../../core/http/api-error';
import { Icon } from '../../shared/ui/icon';

interface ContactRow {
  key: number;
  name: string;
  role: string;
  email: string;
  phone: string;
}

/** "21987654321" → "(21) 98765-4321". */
function formatPhone(digits: string | null | undefined): string {
  if (!digits) return '';
  const m = /^(\d{2})(\d{4,5})(\d{4})$/.exec(digits);
  return m ? `(${m[1]}) ${m[2]}-${m[3]}` : digits;
}

/** Contatos do órgão (pregoeiro, setor de compras...), para o follow-up de pedidos. */
@Component({
  selector: 'app-agency-contacts',
  imports: [FormsModule, Icon],
  template: `
    <section class="contacts" aria-labelledby="contacts-title">
      <header>
        <h3 id="contacts-title">Contatos</h3>
        <span class="cell-sub">Só o necessário para falar com o órgão.</span>
      </header>
      @if (error(); as message) {
        <p class="alert" role="alert">{{ message }}</p>
      }
      @if (saved()) {
        <p class="ok" role="status">Contatos salvos.</p>
      }
      @for (row of rows(); track row.key; let i = $index) {
        <fieldset class="contact">
          <legend class="visually-hidden">Contato {{ i + 1 }}</legend>
          <input
            class="input"
            [name]="'c-name-' + row.key"
            [(ngModel)]="row.name"
            placeholder="Nome"
            maxlength="120"
            [attr.aria-label]="'Nome do contato ' + (i + 1)"
          />
          <input
            class="input"
            [name]="'c-role-' + row.key"
            [(ngModel)]="row.role"
            placeholder="Cargo"
            maxlength="80"
            [attr.aria-label]="'Cargo do contato ' + (i + 1)"
          />
          <input
            class="input"
            type="email"
            [name]="'c-email-' + row.key"
            [(ngModel)]="row.email"
            placeholder="E-mail"
            maxlength="160"
            [attr.aria-label]="'E-mail do contato ' + (i + 1)"
          />
          <input
            class="input"
            type="tel"
            [name]="'c-phone-' + row.key"
            [(ngModel)]="row.phone"
            placeholder="(21) 98765-4321"
            maxlength="25"
            [attr.aria-label]="'Telefone do contato ' + (i + 1)"
          />
          <button
            type="button"
            class="btn ghost icon"
            (click)="remove(row.key)"
            [attr.aria-label]="'Remover contato ' + (i + 1)"
          >
            <app-icon name="x" [size]="16" />
          </button>
        </fieldset>
      } @empty {
        <p class="cell-sub">Nenhum contato cadastrado.</p>
      }
      <div class="actions">
        <button type="button" class="btn sm" (click)="add()">
          <app-icon name="plus" [size]="16" />Adicionar contato
        </button>
        <button
          type="button"
          class="btn sm primary"
          (click)="save()"
          [disabled]="saving()"
          [attr.aria-busy]="saving() || null"
        >
          {{ saving() ? 'Salvando…' : 'Salvar contatos' }}
        </button>
      </div>
    </section>
  `,
  styles: `
    .contacts {
      display: grid;
      gap: 10px;
      padding-top: 16px;
      border-top: 1px solid var(--border);
    }
    header {
      display: grid;
      gap: 2px;
    }
    h3 {
      margin: 0;
      font-size: 14px;
      font-weight: 650;
    }
    .contact {
      display: grid;
      grid-template-columns: 1.2fr 1fr 1.4fr 1fr auto;
      gap: 6px;
      margin: 0;
      padding: 0;
      border: 0;
    }
    .actions {
      display: flex;
      justify-content: space-between;
      gap: 8px;
    }
    .alert {
      margin: 0;
      padding: 8px 12px;
      border-radius: var(--radius-sm);
      background: var(--red-bg);
      color: var(--red);
    }
    .ok {
      margin: 0;
      color: var(--green);
      font-weight: 500;
    }
    @media (max-width: 640px) {
      .contact {
        grid-template-columns: minmax(0, 1fr) auto;
        padding-bottom: 8px;
        border-bottom: 1px solid var(--border);
      }
      .contact .input:not(:first-child) {
        grid-column: 1;
      }
      .contact .btn {
        grid-column: 2;
        grid-row: 1;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AgencyContacts {
  private readonly api = inject(AgenciesService);

  readonly agencyId = input.required<string>();
  /** A versão do órgão sobe ao salvar contatos; o formulário do órgão precisa da nova. */
  readonly versionChange = output<number>();

  protected readonly rows = signal<ContactRow[]>([]);
  protected readonly saving = signal(false);
  protected readonly saved = signal(false);
  protected readonly error = signal<string | null>(null);
  private version = 0;
  private seq = 0;

  constructor() {
    effect(() => {
      const id = this.agencyId();
      this.error.set(null);
      this.saved.set(false);
      this.api.agencyContacts(id).subscribe({
        next: (result) => {
          this.version = result.agencyVersion;
          this.rows.set(result.contacts.map((c) => this.row(c)));
        },
        error: () => this.error.set('Não foi possível carregar os contatos.'),
      });
    });
  }

  protected add(): void {
    this.saved.set(false);
    this.rows.update((r) => [...r, { key: ++this.seq, name: '', role: '', email: '', phone: '' }]);
  }

  protected remove(key: number): void {
    this.saved.set(false);
    this.rows.update((r) => r.filter((row) => row.key !== key));
  }

  protected save(): void {
    const contacts = this.rows()
      .filter((r) => r.name.trim() || r.email.trim() || r.phone.trim())
      .map((r) => ({
        name: r.name.trim(),
        role: r.role.trim() || undefined,
        email: r.email.trim() || undefined,
        phone: r.phone.trim() || undefined,
      }));
    const unnamed = contacts.findIndex((c) => !c.name);
    if (unnamed >= 0) {
      this.error.set(`Informe o nome do contato ${unnamed + 1}.`);
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.saved.set(false);
    this.api.replaceAgencyContacts(this.agencyId(), { version: this.version, contacts }).subscribe({
      next: (result) => {
        this.saving.set(false);
        this.saved.set(true);
        this.version = result.agencyVersion;
        this.rows.set(result.contacts.map((c) => this.row(c)));
        this.versionChange.emit(result.agencyVersion);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível salvar.');
      },
    });
  }

  private row(c: ContactResponse): ContactRow {
    return {
      key: ++this.seq,
      name: c.name,
      role: c.role ?? '',
      email: c.email ?? '',
      phone: formatPhone(c.phone),
    };
  }
}
