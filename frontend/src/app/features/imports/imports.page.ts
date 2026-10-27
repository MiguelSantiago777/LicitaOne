import { DemoBadge } from '../../shared/ui/demo-badge';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Icon } from '../../shared/ui/icon';

@Component({
  selector: 'app-imports-page',
  imports: [DemoBadge, Icon],
  template: `
    <div class="page-head">
      <div>
        <div class="title-line">
          <h1>Importação</h1>
          <app-demo-badge [phase]="2" />
        </div>
        <p class="sub">Traga a planilha de controle de contratos e ajuste depois.</p>
      </div>
    </div>

    <div class="layout">
      <section class="card drop">
        <span class="drop-icon"><app-icon name="spreadsheet" [size]="28" /></span>
        <h2>Arraste a planilha para cá</h2>
        <p class="cell-sub">Arquivos .xlsx ou .csv, até 20 MB</p>
        <button type="button" class="btn primary">
          <app-icon name="imports" [size]="16" />Escolher arquivo
        </button>
      </section>

      <section class="card steps">
        <header class="card-head"><h2>Como funciona</h2></header>
        <ol class="step-list">
          <li>
            <span class="step-n">1</span>
            <div>
              <strong>Envie o arquivo</strong>
              <p class="cell-sub">A tela não trava: o processamento roda em segundo plano.</p>
            </div>
          </li>
          <li>
            <span class="step-n">2</span>
            <div>
              <strong>Confira as colunas</strong>
              <p class="cell-sub">Diga qual coluna é órgão, item, quantidade, valor e pedido.</p>
            </div>
          </li>
          <li>
            <span class="step-n">3</span>
            <div>
              <strong>Revise a prévia</strong>
              <p class="cell-sub">Linhas com erro aparecem destacadas antes de gravar.</p>
            </div>
          </li>
          <li>
            <span class="step-n">4</span>
            <div>
              <strong>Baixe o relatório</strong>
              <p class="cell-sub">
                Um relatório por linha mostra o que entrou e o que ficou de fora.
              </p>
            </div>
          </li>
        </ol>
      </section>
    </div>
  `,
  styles: `
    .layout {
      display: grid;
      grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
      gap: 16px;
      align-items: start;
    }
    .drop {
      display: grid;
      justify-items: center;
      gap: 8px;
      padding: 56px 24px;
      text-align: center;
      border: 2px dashed var(--border-strong);
      box-shadow: none;
      background: var(--surface);
    }
    .drop h2 {
      font-size: 17px;
      font-weight: 650;
      margin-top: 8px;
    }
    .drop .btn {
      margin-top: 12px;
    }
    .drop-icon {
      display: grid;
      place-items: center;
      width: 64px;
      height: 64px;
      border-radius: 16px;
      background: var(--green-bg);
      color: var(--green);
    }
    .step-list {
      display: grid;
      gap: 18px;
      padding: 20px;
    }
    .step-list li {
      display: flex;
      gap: 14px;
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
    @media (max-width: 900px) {
      .layout {
        grid-template-columns: minmax(0, 1fr);
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ImportsPage {}
