import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-price-records-page',
  template: `
    <div class="title-row">
      <div>
        <h1>Atas</h1>
        <p class="sub">
          Atas de registro de preços, por vencimento, e os municípios que pegaram carona.
        </p>
      </div>
    </div>
    <div class="table-wrap">
      <table>
        <caption class="visually-hidden">
          Atas vigentes
        </caption>
        <thead>
          <tr>
            <th scope="col">Ata</th>
            <th scope="col">Órgão</th>
            <th scope="col">Caronas</th>
            <th scope="col" class="n">Vigente até</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td colspan="4">
              <div class="empty">
                <strong>Nenhuma ata cadastrada.</strong>
                <span
                  >Atas e documentos chegam na Fase 3. A ata continua valendo depois que o contrato
                  encerra.</span
                >
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  `,
  styles: `
    @media (max-width: 640px) {
      thead {
        display: none;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PriceRecordsPage {}
