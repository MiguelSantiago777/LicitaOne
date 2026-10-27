import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-imports-page',
  template: `
    <div class="title-row">
      <div>
        <h1>Importação</h1>
        <p class="sub">Traga a planilha de controle de contratos e ajuste depois.</p>
      </div>
    </div>
    <section class="panel" aria-labelledby="imports-history">
      <header>
        <h2 id="imports-history">Importações recentes</h2>
        <span class="meta">0 importações</span>
      </header>
      <div class="empty">
        <strong>Nenhuma planilha importada.</strong>
        <span
          >Envio de .xlsx ou .csv, mapeamento de colunas e relatório de erros por linha chegam na
          Fase 2.</span
        >
      </div>
    </section>
  `,
  styles: `
    .panel {
      max-width: 760px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ImportsPage {}
