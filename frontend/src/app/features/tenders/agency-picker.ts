import { ChangeDetectionStrategy, Component, inject, input, model, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { map, of, switchMap, timer } from 'rxjs';
import { AgenciesService } from '../../api/generated/agencies/agencies.service';
import type { AgencyRefResponse } from '../../api/generated/model';
import { ApiError } from '../../core/http/api-error';
import { UFS } from '../../shared/format';
import { Avatar } from '../../shared/ui/avatar';
import { Icon } from '../../shared/ui/icon';
import { SPHERES } from './tender-meta';

/** Escolha do órgão com busca, e cadastro rápido quando ele ainda não existe. */
@Component({
  selector: 'app-agency-picker',
  imports: [FormsModule, Avatar, Icon],
  template: `
    @if (value(); as agency) {
      <div class="selected">
        <app-avatar [name]="agency.name" [size]="32" [square]="true" />
        <div class="selected-text">
          <span class="cell-main">{{ agency.name }}</span>
          <span class="cell-sub">{{ agency.city }}/{{ agency.state }}</span>
        </div>
        <button type="button" class="btn sm" (click)="value.set(null)">Trocar</button>
      </div>
    } @else if (creating()) {
      <div class="create">
        <div class="field">
          <label [for]="id() + '-name'">Nome do órgão</label>
          <input
            [id]="id() + '-name'"
            class="input"
            [(ngModel)]="newName"
            placeholder="Prefeitura Municipal de..."
          />
        </div>
        <div class="row-3">
          <div class="field">
            <label [for]="id() + '-sphere'">Esfera</label>
            <select [id]="id() + '-sphere'" class="input" [(ngModel)]="newSphere">
              @for (s of spheres; track s.value) {
                <option [value]="s.value">{{ s.label }}</option>
              }
            </select>
          </div>
          <div class="field">
            <label [for]="id() + '-city'">Município</label>
            <input [id]="id() + '-city'" class="input" [(ngModel)]="newCity" />
          </div>
          <div class="field">
            <label [for]="id() + '-uf'">UF</label>
            <select [id]="id() + '-uf'" class="input" [(ngModel)]="newState">
              @for (uf of ufs; track uf) {
                <option [value]="uf">{{ uf }}</option>
              }
            </select>
          </div>
        </div>
        @if (createError()) {
          <p class="error" role="alert">{{ createError() }}</p>
        }
        <div class="create-actions">
          <button type="button" class="btn sm ghost" (click)="creating.set(false)">Cancelar</button>
          <button
            type="button"
            class="btn sm primary"
            (click)="create()"
            [disabled]="saving()"
            [attr.aria-busy]="saving() || null"
          >
            {{ saving() ? 'Salvando…' : 'Cadastrar órgão' }}
          </button>
        </div>
      </div>
    } @else {
      <div class="search">
        <app-icon name="search" [size]="16" />
        <input
          [id]="id()"
          class="input"
          type="search"
          autocomplete="off"
          placeholder="Buscar órgão por nome ou município"
          [ngModel]="query()"
          (ngModelChange)="query.set($event)"
          [attr.aria-invalid]="invalid()"
        />
      </div>
      @if (query().trim().length >= 2) {
        <ul class="options" role="listbox" aria-label="Órgãos encontrados">
          @for (a of results.value() ?? []; track a.id) {
            <li>
              <button type="button" role="option" aria-selected="false" (click)="pick(a)">
                <app-avatar [name]="a.name" [size]="28" [square]="true" />
                <span class="cell-main">{{ a.name }}</span>
                <span class="cell-sub">{{ a.city }}/{{ a.state }}</span>
              </button>
            </li>
          } @empty {
            @if (!results.isLoading()) {
              <li class="none">Nenhum órgão encontrado.</li>
            }
          }
          <li>
            <button type="button" class="new" (click)="startCreate()">
              <app-icon name="plus" [size]="16" />Cadastrar “{{ query().trim() }}”
            </button>
          </li>
        </ul>
      }
    }
  `,
  styles: `
    .selected {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 12px;
      border: 1px solid var(--border-strong);
      border-radius: var(--radius-sm);
    }
    .selected-text {
      display: grid;
      flex: 1;
      min-width: 0;
    }
    .options {
      margin-top: 6px;
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
      box-shadow: var(--shadow);
      max-height: 260px;
      overflow-y: auto;
    }
    .options button {
      display: flex;
      align-items: center;
      gap: 10px;
      width: 100%;
      padding: 8px 12px;
      border: 0;
      background: none;
      text-align: left;
      cursor: pointer;
    }
    .options button:hover,
    .options button:focus-visible {
      background: var(--hover);
    }
    .options .cell-main {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .options .new {
      color: var(--brand);
      font-weight: 600;
      border-top: 1px solid var(--border);
    }
    .none {
      padding: 10px 12px;
      color: var(--text-3);
    }
    .create {
      display: grid;
      gap: 12px;
      padding: 14px;
      border-radius: var(--radius-sm);
      background: var(--surface-2);
      border: 1px solid var(--border);
    }
    .row-3 {
      display: grid;
      grid-template-columns: 1fr 1.4fr 90px;
      gap: 10px;
    }
    .create-actions {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
    }
    .error {
      color: var(--red);
      font-size: 13px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AgencyPicker {
  private readonly api = inject(AgenciesService);

  readonly value = model<AgencyRefResponse | null>(null);
  readonly id = input('agency');
  readonly invalid = input(false);

  protected readonly spheres = SPHERES;
  protected readonly ufs = UFS;
  protected readonly query = signal('');
  protected readonly creating = signal(false);
  protected readonly saving = signal(false);
  protected readonly createError = signal<string | null>(null);
  protected newName = '';
  protected newSphere = 'MUNICIPAL';
  protected newCity = '';
  protected newState = 'RJ';

  protected readonly results = rxResource({
    params: () => this.query().trim(),
    stream: ({ params }) =>
      params.length < 2
        ? of([] as AgencyRefResponse[])
        : timer(250).pipe(
            switchMap(() => this.api.searchAgencies({ q: params, size: 8 })),
            map((page) =>
              page.items.map((a) => ({ id: a.id, name: a.name, city: a.city, state: a.state })),
            ),
          ),
  });

  protected pick(agency: AgencyRefResponse): void {
    this.value.set(agency);
    this.query.set('');
  }

  protected startCreate(): void {
    this.newName = this.query().trim();
    this.createError.set(null);
    this.creating.set(true);
  }

  protected create(): void {
    this.saving.set(true);
    this.createError.set(null);
    this.api
      .createAgency({
        name: this.newName,
        sphere: this.newSphere as 'MUNICIPAL',
        city: this.newCity,
        state: this.newState,
      })
      .subscribe({
        next: (a) => {
          this.saving.set(false);
          this.creating.set(false);
          this.pick({ id: a.id, name: a.name, city: a.city, state: a.state });
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.createError.set(
            err instanceof ApiError
              ? (err.fieldErrors[0]?.message ?? err.detail)
              : 'Não foi possível salvar.',
          );
        },
      });
  }
}
