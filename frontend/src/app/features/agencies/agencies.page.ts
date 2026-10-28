import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AgenciesService } from '../../api/generated/agencies/agencies.service';
import type { AgencyRequest, AgencyResponse } from '../../api/generated/model';
import { Session } from '../../core/auth/session';
import { ApiError } from '../../core/http/api-error';
import { UFS } from '../../shared/format';
import { Avatar } from '../../shared/ui/avatar';
import { Icon } from '../../shared/ui/icon';
import { Pager } from '../../shared/ui/pager';
import { Panel } from '../../shared/ui/panel';
import { SPHERES } from '../tenders/tender-meta';
import { AgencyContacts } from './agency-contacts';

@Component({
  selector: 'app-agencies-page',
  imports: [FormsModule, RouterLink, Avatar, Icon, Pager, Panel, AgencyContacts],
  templateUrl: './agencies.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .toolbar {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 14px 16px;
      border-bottom: 1px solid var(--border);
    }
    .toolbar .search {
      width: min(380px, 100%);
    }
    .form {
      display: grid;
      gap: 14px;
    }
    .row {
      display: grid;
      grid-template-columns: 1fr 1.4fr 90px;
      gap: 10px;
    }
    .textarea {
      height: auto;
      padding: 10px 12px;
    }
    .error {
      color: var(--red);
      font-weight: 500;
    }
    tr.clickable {
      cursor: pointer;
    }
    @media (max-width: 560px) {
      .row {
        grid-template-columns: minmax(0, 1fr);
      }
    }
  `,
})
export class AgenciesPage {
  private readonly api = inject(AgenciesService);
  protected readonly session = inject(Session);
  protected readonly spheres = SPHERES;
  protected readonly ufs = UFS;

  protected readonly q = signal('');
  protected readonly page = signal(0);
  protected readonly list = rxResource({
    params: () => ({ q: this.q() || undefined, page: this.page(), size: 25 }),
    stream: ({ params }) => this.api.searchAgencies(params),
  });

  protected readonly editing = signal<AgencyResponse | null>(null);
  protected readonly formOpen = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected form: AgencyRequest = this.blank();
  protected readonly canEdit = computed(() => this.session.user()?.role !== 'VIEWER');

  private timer?: ReturnType<typeof setTimeout>;
  protected onSearch(event: Event): void {
    clearTimeout(this.timer);
    const value = (event.target as HTMLInputElement).value;
    this.timer = setTimeout(() => {
      this.page.set(0);
      this.q.set(value.trim());
    }, 300);
  }

  protected openNew(): void {
    this.editing.set(null);
    this.form = this.blank();
    this.error.set(null);
    this.formOpen.set(true);
  }

  protected openEdit(a: AgencyResponse): void {
    if (!this.canEdit()) return;
    this.editing.set(a);
    this.form = {
      name: a.name,
      sphere: a.sphere,
      city: a.city,
      state: a.state,
      cnpj: a.cnpj ?? '',
      defaultUasg: a.defaultUasg ?? '',
      notes: a.notes ?? '',
      version: a.version,
    };
    this.error.set(null);
    this.formOpen.set(true);
  }

  protected save(): void {
    this.saving.set(true);
    this.error.set(null);
    const body: AgencyRequest = {
      ...this.form,
      cnpj: this.form.cnpj || undefined,
      defaultUasg: this.form.defaultUasg || undefined,
      notes: this.form.notes || undefined,
    };
    const current = this.editing();
    const request = current ? this.api.updateAgency(current.id, body) : this.api.createAgency(body);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.list.reload();
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(
          err instanceof ApiError
            ? (err.fieldErrors[0]?.message ?? err.detail)
            : 'Não foi possível salvar.',
        );
      },
    });
  }

  private blank(): AgencyRequest {
    return {
      name: '',
      sphere: 'MUNICIPAL',
      city: '',
      state: 'RJ',
      cnpj: '',
      defaultUasg: '',
      notes: '',
    };
  }
}
