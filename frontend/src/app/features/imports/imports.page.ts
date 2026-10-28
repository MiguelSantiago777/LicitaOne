import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';
import { ContractsService } from '../../api/generated/contracts/contracts.service';
import type { ContractResponse, TenderResponse } from '../../api/generated/model';
import { TendersService } from '../../api/generated/tenders/tenders.service';
import { ApiError } from '../../core/http/api-error';
import { Icon } from '../../shared/ui/icon';
import { ImportWizard } from '../tenders/contracts/import-wizard';

/**
 * Importação de planilhas: escolha a licitação e o contrato, e o assistente cuida do resto (o mesmo da aba
 * "Itens e pedidos" do contrato).
 */
@Component({
  selector: 'app-imports-page',
  imports: [CurrencyPipe, FormsModule, Icon, ImportWizard],
  template: `
    <div class="page-head">
      <div>
        <h1>Importação</h1>
        <p class="sub">
          Traga os itens e os pedidos de um contrato da planilha de controle e ajuste depois.
        </p>
      </div>
    </div>

    <div class="layout">
      <section class="card pick">
        <header class="card-head">
          <h2>Escolha o contrato</h2>
          <span class="cell-sub">Licitações com contrato assinado ou em andamento</span>
        </header>
        <div class="card-body">
          <label class="search">
            <app-icon name="search" [size]="16" />
            <input
              class="input"
              type="search"
              placeholder="Buscar órgão, UASG, pregão ou material"
              aria-label="Buscar licitação"
              [ngModel]="query()"
              (ngModelChange)="typed$.next($event)"
            />
          </label>
          @if (error(); as message) {
            <p class="alert" role="alert">{{ message }}</p>
          }
          @if (tenders.isLoading() && !tenders.hasValue()) {
            <p class="cell-sub">Carregando…</p>
          } @else {
            <ul class="tender-list">
              @for (t of tenders.value()?.items ?? []; track t.id) {
                <li>
                  <div class="t-main">
                    <span class="cell-main">{{ t.agency.name }}</span>
                    <span class="cell-sub">{{ t.reference }} · {{ t.materialType }}</span>
                  </div>
                  <span class="num t-value">{{ t.totalValue | currency: 'BRL' }}</span>
                  @if (t.canEdit) {
                    <button
                      type="button"
                      class="btn sm"
                      (click)="choose(t)"
                      [disabled]="loadingTender() === t.id"
                    >
                      <app-icon name="imports" [size]="16" />Importar
                    </button>
                  } @else {
                    <span class="cell-sub">Só o responsável</span>
                  }
                </li>
                @if (choices()?.tender?.id === t.id) {
                  <li class="choices">
                    <span class="cell-sub">Esta licitação tem mais de um contrato aberto:</span>
                    @for (c of choices()!.contracts; track c.id) {
                      <button type="button" class="btn sm" (click)="open(t, c)">
                        {{ c.kindLabel }} {{ c.number ?? '' }} ·
                        {{ c.value | currency: 'BRL' }}
                      </button>
                    }
                  </li>
                }
              } @empty {
                <li class="empty">
                  <strong>Nenhuma licitação com contrato aberto.</strong>
                  <span>Registre o contrato na licitação vencida e volte aqui.</span>
                </li>
              }
            </ul>
          }
        </div>
      </section>

      <section class="card steps">
        <header class="card-head"><h2>Como funciona</h2></header>
        <ol class="step-list">
          <li>
            <span class="step-n">1</span>
            <div>
              <strong>Envie a planilha</strong>
              <p class="cell-sub">
                A aba do órgão na planilha de controle (.xlsx) ou um .csv, até 10 MB.
              </p>
            </div>
          </li>
          <li>
            <span class="step-n">2</span>
            <div>
              <strong>Confira as colunas</strong>
              <p class="cell-sub">
                Descrição, quantidade, V.U., lote e pedido já são reconhecidos pelo título.
              </p>
            </div>
          </li>
          <li>
            <span class="step-n">3</span>
            <div>
              <strong>Revise a prévia</strong>
              <p class="cell-sub">Cada linha mostra se o item é novo, se muda ou se tem erro.</p>
            </div>
          </li>
          <li>
            <span class="step-n">4</span>
            <div>
              <strong>Importe e baixe o relatório</strong>
              <p class="cell-sub">
                Roda em segundo plano. Importar de novo a mesma planilha não duplica nada.
              </p>
            </div>
          </li>
        </ol>
        <p class="note">
          A aba GERAL (uma linha por licitação) ainda não é importada: falta UASG ou número do
          pregão para identificar cada licitação.
        </p>
      </section>
    </div>

    <app-import-wizard
      [contract]="importing()"
      [agencyName]="importingAgency()"
      (closed)="importing.set(null)"
    />
  `,
  styles: `
    .layout {
      display: grid;
      grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
      gap: 16px;
      align-items: start;
    }
    .card-head {
      display: grid;
      gap: 2px;
    }
    .card-body {
      display: grid;
      gap: 12px;
    }
    .search {
      position: relative;
      display: block;
    }
    .search app-icon {
      position: absolute;
      left: 12px;
      top: 50%;
      translate: 0 -50%;
      color: var(--text-3);
    }
    .search .input {
      padding-left: 36px;
    }
    .alert {
      margin: 0;
      padding: 10px 14px;
      border-radius: var(--radius-sm);
      background: var(--red-bg);
      color: var(--red);
    }
    .tender-list {
      display: grid;
      margin: 0;
      padding: 0;
      list-style: none;
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
    }
    .tender-list li {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 12px;
      border-top: 1px solid var(--border);
    }
    .tender-list li:first-child {
      border-top: 0;
    }
    .t-main {
      display: grid;
      flex: 1;
      min-width: 0;
    }
    .t-value {
      color: var(--text-2);
      white-space: nowrap;
    }
    .choices {
      flex-wrap: wrap;
      background: var(--surface-2);
    }
    .step-list {
      display: grid;
      gap: 18px;
      margin: 0;
      padding: 20px;
      list-style: none;
    }
    .step-list li {
      display: flex;
      gap: 14px;
    }
    .step-list p {
      margin: 2px 0 0;
    }
    .step-n {
      display: grid;
      place-items: center;
      flex: none;
      width: 28px;
      height: 28px;
      border-radius: 50%;
      background: var(--brand-soft);
      color: var(--brand);
      font-weight: 700;
      font-size: 13px;
    }
    .note {
      margin: 0 20px 20px;
      padding: 10px 14px;
      border-radius: var(--radius-sm);
      background: var(--surface-2);
      border: 1px solid var(--border);
      color: var(--text-2);
      font-size: 13px;
    }
    @media (max-width: 900px) {
      .layout {
        grid-template-columns: minmax(0, 1fr);
      }
    }
    @media (max-width: 560px) {
      .t-value {
        display: none;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ImportsPage {
  private readonly tendersApi = inject(TendersService);
  private readonly contractsApi = inject(ContractsService);

  protected readonly typed$ = new Subject<string>();
  protected readonly query = signal('');
  protected readonly loadingTender = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly choices = signal<{
    tender: TenderResponse;
    contracts: ContractResponse[];
  } | null>(null);
  protected readonly importing = signal<ContractResponse | null>(null);
  private readonly importingTender = signal<TenderResponse | null>(null);
  protected readonly importingAgency = computed(() => this.importingTender()?.agency.name ?? null);

  protected readonly tenders = rxResource({
    params: () => this.query(),
    stream: ({ params }) =>
      this.tendersApi.searchTenders({
        status: ['CONTRATO_ASSINADO', 'EM_ANDAMENTO'],
        q: params || undefined,
        size: 20,
        sort: 'agency',
        direction: 'asc',
      }),
  });

  constructor() {
    this.typed$
      .pipe(debounceTime(250), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((q) => this.query.set(q));
  }

  /** Com um contrato aberto, abre o assistente direto; com vários, pergunta qual. */
  protected choose(tender: TenderResponse): void {
    this.error.set(null);
    this.choices.set(null);
    this.loadingTender.set(tender.id);
    this.contractsApi.tenderContracts(tender.id).subscribe({
      next: (contracts) => {
        this.loadingTender.set(null);
        const open = contracts.filter((c) => !c.closedAt);
        if (open.length === 1) {
          this.open(tender, open[0]);
        } else if (open.length > 1) {
          this.choices.set({ tender, contracts: open });
        } else {
          this.error.set('Esta licitação não tem contrato aberto.');
        }
      },
      error: (err: unknown) => {
        this.loadingTender.set(null);
        this.error.set(err instanceof ApiError ? err.detail : 'Não foi possível carregar.');
      },
    });
  }

  protected open(tender: TenderResponse, contract: ContractResponse): void {
    this.choices.set(null);
    this.importingTender.set(tender);
    this.importing.set(contract);
  }
}
