import { CurrencyPipe, DatePipe } from '@angular/common';
import { DemoBadge } from '../../shared/ui/demo-badge';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Avatar } from '../../shared/ui/avatar';
import { Icon } from '../../shared/ui/icon';
import { TENDERS, daysBetween } from '../demo/demo-data';

@Component({
  selector: 'app-price-records-page',
  imports: [DemoBadge, CurrencyPipe, DatePipe, Avatar, Icon],
  template: `
    <div class="page-head">
      <div>
        <div class="title-line">
          <h1>Atas</h1>
          <app-demo-badge [phase]="3" />
        </div>
        <p class="sub">
          Atas de registro de preços vigentes. Cada uma pode virar carona em outro município.
        </p>
      </div>
      <div class="page-actions">
        <button type="button" class="btn primary">
          <app-icon name="plus" [size]="16" />Anexar ata
        </button>
      </div>
    </div>

    <div class="records">
      @for (r of records; track r.id) {
        <article class="card record">
          <div class="record-head">
            <app-avatar [name]="r.agency" [size]="40" [square]="true" />
            <div class="record-title">
              <h2>{{ r.agency }}</h2>
              <p class="cell-sub">{{ r.uasgPregao }} · {{ r.material }}</p>
            </div>
            <span class="badge" [class.amber]="r.daysLeft <= 120" [class.violet]="r.daysLeft > 120">
              {{ r.daysLeft <= 120 ? 'Vence em ' + r.daysLeft + ' dias' : 'Válida' }}
            </span>
          </div>
          <dl class="record-figures">
            <div>
              <dt>Vigente até</dt>
              <dd class="num">{{ r.ataUntil | date: 'dd/MM/yyyy' }}</dd>
            </div>
            <div>
              <dt>Valor registrado</dt>
              <dd class="num">{{ r.value | currency: 'BRL' : 'symbol' : '1.0-0' }}</dd>
            </div>
            <div>
              <dt>Caronas</dt>
              <dd class="num">{{ r.caronas }} de 4</dd>
            </div>
          </dl>
          <div class="caronas">
            @for (slot of [0, 1, 2, 3]; track slot) {
              <span class="slot" [class.used]="slot < r.caronas"></span>
            }
          </div>
          <div class="record-foot">
            <button type="button" class="btn sm">
              <app-icon name="tenders" [size]="14" />Ver ata
            </button>
            <button type="button" class="btn sm">
              <app-icon name="handshake" [size]="14" />Oferecer carona
            </button>
          </div>
        </article>
      }
    </div>
  `,
  styles: `
    .records {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
      gap: 16px;
    }
    .record {
      padding: 20px;
      display: grid;
      gap: 16px;
    }
    .record-head {
      display: flex;
      align-items: flex-start;
      gap: 12px;
    }
    .record-title {
      flex: 1;
      min-width: 0;
    }
    .record-title h2 {
      font-size: 15px;
      font-weight: 650;
      line-height: 22px;
    }
    .record-figures {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 12px;
      margin: 0;
      padding: 14px;
      border-radius: 10px;
      background: var(--surface-2);
    }
    .record-figures dt {
      color: var(--text-3);
      font-size: 12px;
    }
    .record-figures dd {
      margin: 2px 0 0;
      font-weight: 650;
    }
    .caronas {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 6px;
    }
    .slot {
      height: 6px;
      border-radius: 999px;
      background: var(--hover);
    }
    .slot.used {
      background: var(--violet);
    }
    .record-foot {
      display: flex;
      gap: 8px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PriceRecordsPage {
  protected readonly records = TENDERS.filter((t) => t.ataUntil && daysBetween(t.ataUntil) < 0)
    .map((t, i) => ({ ...t, daysLeft: -daysBetween(t.ataUntil!), caronas: [1, 0, 2, 0, 1][i % 5] }))
    .sort((a, b) => a.daysLeft - b.daysLeft);
}
