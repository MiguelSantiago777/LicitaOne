import { CurrencyPipe, DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import type {
  ProposalItemRequest,
  ProposalRequest,
  ProposalResponse,
} from '../../api/generated/model';
import { ProposalsService } from '../../api/generated/proposals/proposals.service';
import { UsersService } from '../../api/generated/users/users.service';
import { Session } from '../../core/auth/session';
import { ApiError } from '../../core/http/api-error';
import { Notices } from '../../core/notices/notices';
import { formatQuantity, parseQuantity } from '../../shared/format';
import { Icon } from '../../shared/ui/icon';
import { saveBlob } from '../price-records/documents-section';
import {
  formatCnpj,
  formatCpf,
  formatUnitPrice,
  KINDS,
  lineTotal,
  parseUnitPrice,
  type ProposalKind,
} from './proposal-format';

/** Uma linha de item como a pessoa digita: números ficam em texto até salvar. */
interface Row {
  key: number;
  itemNumber: string;
  description: string;
  unit: string;
  brand: string;
  quantity: string;
  unitPrice: string;
}

interface Form {
  kind: ProposalKind;
  clientName: string;
  clientCnpj: string;
  clientAddress: string;
  clientEmail: string;
  clientPhones: string;
  validityDays: string;
  paymentTerms: string;
  deliveryTerms: string;
  proposalDate: string;
  signerName: string;
  signerRole: string;
  signerCpf: string;
}

type SaveState = 'idle' | 'pending' | 'saving' | 'saved' | 'error' | 'conflict';

const SAVE_DELAY_MS = 800;

/**
 * Edição da proposta. Salva sozinha (800 ms depois da última alteração) mandando a proposta inteira com a versão; o
 * servidor devolve o que falta para o PDF e o total por extenso. O número nunca muda. Cancelada ou perfil Leitura:
 * só leitura.
 */
@Component({
  selector: 'app-proposal-page',
  imports: [CurrencyPipe, DatePipe, FormsModule, RouterLink, Icon],
  templateUrl: './proposal.page.html',
  styleUrl: './proposal.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProposalPage {
  private readonly api = inject(ProposalsService);
  private readonly usersApi = inject(UsersService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notices = inject(Notices);
  private readonly session = inject(Session);

  protected readonly kinds = KINDS;
  private id = '';

  protected readonly proposal = signal<ProposalResponse | null>(null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly companyMissing = rxResource({
    stream: () => this.api.proposalCompanyWarning(),
  });

  protected form: Form = emptyForm();
  protected rows: Row[] = [];
  /** Muda a cada tecla: os totais da tela dependem dele, já que o formulário é um objeto comum. */
  private readonly revision = signal(0);
  private nextKey = 1;

  protected readonly saveState = signal<SaveState>('idle');
  protected readonly saveError = signal<string | null>(null);
  private timer: ReturnType<typeof setTimeout> | null = null;
  private saving = false;
  private again = false;

  protected readonly busy = signal(false);
  protected readonly actionError = signal<string | null>(null);
  protected readonly confirmingCancel = signal(false);

  protected readonly editable = computed(() => this.proposal()?.canEdit ?? false);
  /** Administrador e Comercial; a cancelada vem com canEdit falso, mas ainda dá para duplicar ou reabrir. */
  protected readonly canWrite = computed(() => this.session.user()?.role !== 'VIEWER');

  protected readonly totals = computed(() => {
    this.revision();
    const lines = this.rows.map((r) =>
      lineTotal(parseQuantity(r.quantity), parseUnitPrice(r.unitPrice)),
    );
    const total = lines.reduce<number>((sum, t) => sum + (t ?? 0), 0);
    return { lines, total: Math.round(total * 100) / 100 };
  });

  /** Campos com número que não deu para ler: a tela marca em vermelho e o servidor recebe vazio. */
  protected readonly invalid = computed(() => {
    this.revision();
    const set = new Set<string>();
    this.rows.forEach((r) => {
      if (r.quantity.trim() && parseQuantity(r.quantity) == null) set.add(`q${r.key}`);
      if (r.unitPrice.trim() && parseUnitPrice(r.unitPrice) == null) set.add(`p${r.key}`);
    });
    const days = this.form.validityDays.trim();
    if (days && !/^\d{1,4}$/.test(days)) set.add('validity');
    return set;
  });

  protected readonly canDownload = computed(
    () =>
      !!this.proposal() &&
      !this.proposal()!.cancelledAt &&
      this.proposal()!.missing.length === 0 &&
      (this.saveState() === 'saved' || this.saveState() === 'idle'),
  );

  constructor() {
    // Duplicar navega para a mesma rota com outro id: o Angular reaproveita o componente
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.id = params.get('id') ?? '';
      this.proposal.set(null);
      this.saveState.set('idle');
      this.saveError.set(null);
      this.confirmingCancel.set(false);
      this.load();
    });
    inject(DestroyRef).onDestroy(() => {
      // Saiu da tela com alteração esperando: salva agora
      if (this.timer) {
        clearTimeout(this.timer);
        this.flush();
      }
    });
  }

  private load(): void {
    this.api.getProposal(this.id).subscribe({
      next: (p) => this.apply(p, true),
      error: (err: unknown) =>
        this.loadError.set(
          err instanceof ApiError ? err.detail : 'Não foi possível abrir a proposta.',
        ),
    });
  }

  /** Recebe a proposta do servidor. Com {@code fill}, troca também o que está nos campos. */
  private apply(p: ProposalResponse, fill: boolean): void {
    this.proposal.set(p);
    if (fill) {
      this.form = {
        kind: p.kind as ProposalKind,
        clientName: p.clientName ?? '',
        clientCnpj: formatCnpj(p.clientCnpj),
        clientAddress: p.clientAddress ?? '',
        clientEmail: p.clientEmail ?? '',
        clientPhones: p.clientPhones ?? '',
        validityDays: p.validityDays == null ? '' : String(p.validityDays),
        paymentTerms: p.paymentTerms ?? '',
        deliveryTerms: p.deliveryTerms ?? '',
        proposalDate: p.proposalDate,
        signerName: p.signerName ?? '',
        signerRole: p.signerRole ?? '',
        signerCpf: formatCpf(p.signerCpf),
      };
      this.rows = p.items.map((i) => ({
        key: this.nextKey++,
        itemNumber: i.itemNumber ?? '',
        description: i.description ?? '',
        unit: i.unit ?? '',
        brand: i.brand ?? '',
        quantity: formatQuantity(i.quantity ?? null),
        unitPrice: formatUnitPrice(i.unitPrice ?? null),
      }));
      this.revision.update((n) => n + 1);
    }
  }

  protected changed(): void {
    if (!this.editable()) return;
    this.revision.update((n) => n + 1);
    this.saveState.set('pending');
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => {
      this.timer = null;
      this.flush();
    }, SAVE_DELAY_MS);
  }

  /** Salva já. Se um salvamento está em andamento, salva de novo quando ele terminar. */
  private flush(): void {
    const current = this.proposal();
    if (!current) return;
    if (this.saving) {
      this.again = true;
      return;
    }
    this.saving = true;
    this.saveState.set('saving');
    this.api
      .updateProposal(this.id, { version: current.version, proposal: this.request() })
      .subscribe({
        next: (p) => {
          this.saving = false;
          this.apply(p, false);
          this.saveError.set(null);
          if (this.again) {
            this.again = false;
            this.flush();
          } else if (!this.timer) {
            this.saveState.set('saved');
          }
        },
        error: (err: unknown) => {
          this.saving = false;
          this.again = false;
          if (err instanceof ApiError && err.code === 'VERSION_CONFLICT') {
            this.saveState.set('conflict');
            this.saveError.set(err.detail);
            return;
          }
          this.saveState.set('error');
          this.saveError.set(
            err instanceof ApiError
              ? (err.fieldErrors[0]?.message ?? err.detail)
              : 'Não foi possível salvar. Confira a conexão.',
          );
        },
      });
  }

  private request(): ProposalRequest {
    const f = this.form;
    const text = (v: string) => v.trim() || undefined;
    const days = f.validityDays.trim();
    return {
      kind: f.kind,
      clientName: text(f.clientName),
      clientCnpj: text(f.clientCnpj),
      clientAddress: text(f.clientAddress),
      clientEmail: text(f.clientEmail),
      clientPhones: text(f.clientPhones),
      validityDays: /^\d{1,4}$/.test(days) && Number(days) > 0 ? Number(days) : undefined,
      paymentTerms: text(f.paymentTerms),
      deliveryTerms: text(f.deliveryTerms),
      proposalDate: f.proposalDate,
      signerName: text(f.signerName),
      signerRole: text(f.signerRole),
      signerCpf: text(f.signerCpf),
      items: this.rows.map((r): ProposalItemRequest => ({
        itemNumber: text(r.itemNumber),
        description: text(r.description),
        unit: text(r.unit),
        brand: text(r.brand),
        quantity: parseQuantity(r.quantity) ?? undefined,
        unitPrice: parseUnitPrice(r.unitPrice) ?? undefined,
      })),
    };
  }

  protected addItem(): void {
    const key = this.nextKey++;
    this.rows = [
      ...this.rows,
      { key, itemNumber: '', description: '', unit: '', brand: '', quantity: '', unitPrice: '' },
    ];
    this.changed();
    setTimeout(() => document.getElementById(`desc-${key}`)?.focus());
  }

  protected removeItem(row: Row): void {
    this.rows = this.rows.filter((r) => r !== row);
    this.changed();
  }

  /** Formata CNPJ, CPF e números ao sair do campo, sem mudar o valor. */
  protected tidy(field: 'cnpj' | 'cpf'): void {
    if (field === 'cnpj') this.form.clientCnpj = formatCnpj(this.form.clientCnpj);
    else this.form.signerCpf = formatCpf(this.form.signerCpf);
  }

  protected tidyRow(row: Row): void {
    const q = parseQuantity(row.quantity);
    if (q != null) row.quantity = formatQuantity(q);
    const p = parseUnitPrice(row.unitPrice);
    if (p != null) row.unitPrice = formatUnitPrice(p);
  }

  protected download(): void {
    const p = this.proposal();
    if (!p) return;
    this.busy.set(true);
    this.actionError.set(null);
    this.api.downloadProposalPdf(p.id).subscribe({
      next: (blob) => {
        this.busy.set(false);
        saveBlob(blob, p.fileName);
      },
      error: () => {
        this.busy.set(false);
        this.actionError.set('Não foi possível gerar o PDF. Recarregue a página e tente de novo.');
      },
    });
  }

  protected duplicate(): void {
    const p = this.proposal();
    if (!p) return;
    this.busy.set(true);
    this.api.duplicateProposal(p.id).subscribe({
      next: (copy) => {
        this.busy.set(false);
        this.notices.show({
          tone: 'info',
          message: `Proposta ${copy.number} criada a partir da ${p.number}.`,
        });
        void this.router.navigate(['/propostas', copy.id]);
      },
      error: (err: unknown) => this.fail(err),
    });
  }

  protected cancel(): void {
    const p = this.proposal();
    if (!p) return;
    this.busy.set(true);
    this.api.cancelProposal(p.id, { version: p.version }).subscribe({
      next: (v) => {
        this.busy.set(false);
        this.confirmingCancel.set(false);
        this.apply(v, false);
      },
      error: (err: unknown) => this.fail(err),
    });
  }

  protected reopen(): void {
    const p = this.proposal();
    if (!p) return;
    this.busy.set(true);
    this.api.reopenProposal(p.id, { version: p.version }).subscribe({
      next: (v) => {
        this.busy.set(false);
        this.apply(v, false);
      },
      error: (err: unknown) => this.fail(err),
    });
  }

  /** Guarda o cargo e o CPF deste responsável no cadastro do usuário, para as próximas propostas. */
  protected saveAsMyDefault(): void {
    this.busy.set(true);
    this.usersApi
      .updateOwnProfile({
        jobTitle: this.form.signerRole.trim() || undefined,
        cpf: this.form.signerCpf.trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.notices.show({
            tone: 'info',
            message: 'Cargo e CPF salvos no seu cadastro. As próximas propostas já vêm com eles.',
          });
        },
        error: (err: unknown) => this.fail(err),
      });
  }

  protected reloadPage(): void {
    this.saveState.set('idle');
    this.saveError.set(null);
    this.load();
  }

  private fail(err: unknown): void {
    this.busy.set(false);
    this.actionError.set(err instanceof ApiError ? err.detail : 'Não foi possível concluir.');
  }
}

function emptyForm(): Form {
  return {
    kind: 'PRODUTO',
    clientName: '',
    clientCnpj: '',
    clientAddress: '',
    clientEmail: '',
    clientPhones: '',
    validityDays: '',
    paymentTerms: '',
    deliveryTerms: '',
    proposalDate: '',
    signerName: '',
    signerRole: '',
    signerCpf: '',
  };
}
