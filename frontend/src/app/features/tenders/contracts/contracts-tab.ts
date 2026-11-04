import { CurrencyPipe, DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { ContractsService } from '../../../api/generated/contracts/contracts.service';
import { DocumentsService } from '../../../api/generated/documents/documents.service';
import type {
  ContractOrderResponse,
  ContractResponse,
  ExtractionItem,
  TenderResponse,
} from '../../../api/generated/model';
import { ApiError } from '../../../core/http/api-error';
import { daysSince, formatQuantity } from '../../../shared/format';
import { Icon } from '../../../shared/ui/icon';
import { saveBlob } from '../../price-records/documents-section';
import { CloseContractDialog } from './close-contract-dialog';
import { ImportWizard } from './import-wizard';
import { ItemsEditor } from './items-editor';
import { OrderDialog } from './order-dialog';
import { SignContractDialog } from './sign-contract-dialog';

/** Status da licitação em que ela recebe contratos (ver TenderStatus.acceptsContracts no backend). */
const ACCEPTS_CONTRACTS = ['VENCIDA', 'AGUARDANDO_ASSINATURA', 'CONTRATO_ASSINADO', 'EM_ANDAMENTO'];

/**
 * Aba "Itens e pedidos" do detalhe da licitação: contratos (ou empenhos) com itens, pedidos e saldo, e as ações
 * de registrar contrato, pedido, itens, importação da planilha e encerramento.
 */
@Component({
  selector: 'app-contracts-tab',
  imports: [
    CurrencyPipe,
    DatePipe,
    Icon,
    SignContractDialog,
    OrderDialog,
    CloseContractDialog,
    ItemsEditor,
    ImportWizard,
  ],
  templateUrl: './contracts-tab.html',
  styleUrl: './contracts-tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContractsTab {
  private readonly api = inject(ContractsService);
  private readonly documentsApi = inject(DocumentsService);

  readonly tender = input.required<TenderResponse>();
  /** Muda quando o cabeçalho do detalhe pede "Registrar contrato": abre o diálogo. */
  readonly signRequest = input(0);
  /** Algo mudou nos contratos que pode ter mudado a licitação (situação, datas, versão). */
  readonly changed = output<void>();

  protected readonly fq = formatQuantity;
  protected readonly signing = signal(false);
  protected readonly editingContract = signal<ContractResponse | null>(null);
  protected readonly ordering = signal<ContractResponse | null>(null);
  protected readonly closing = signal<ContractResponse | null>(null);
  protected readonly editingItems = signal<ContractResponse | null>(null);
  protected readonly importing = signal<ContractResponse | null>(null);
  /** Itens lidos pela IA no PDF do contrato, oferecidos no editor de itens depois de salvar. */
  protected readonly suggestedItems = signal<ExtractionItem[] | null>(null);
  /** Pedido aguardando a confirmação para ser apagado (confirmação na própria linha, sem diálogo do navegador). */
  protected readonly confirmingRemoval = signal<string | null>(null);
  protected readonly actionError = signal<string | null>(null);

  protected readonly contracts = rxResource({
    params: () => this.tender().id,
    stream: ({ params }) => this.api.tenderContracts(params),
  });

  /** Documentos da licitação, só quando algum pedido veio de um (para o nome e o download). */
  protected readonly documents = rxResource({
    params: () =>
      (this.contracts.value() ?? []).some((c) => c.orders.some((o) => o.sourceDocumentId))
        ? this.tender().id
        : undefined,
    stream: ({ params }) => this.documentsApi.listDocuments({ tenderId: params }),
  });

  protected readonly canSign = computed(
    () => this.tender().canEdit && ACCEPTS_CONTRACTS.includes(this.tender().status),
  );

  protected readonly totals = computed(() => {
    const list = this.contracts.value() ?? [];
    const value = list.reduce((s, c) => s + c.value, 0);
    const ordered = list.reduce((s, c) => s + c.ordered, 0);
    return { value, ordered, remaining: value - ordered };
  });

  constructor() {
    effect(() => {
      if (this.signRequest() > 0) untracked(() => this.signing.set(true));
    });
  }

  protected daysWithoutOrder(c: ContractResponse): number {
    return c.orders.length || c.closedAt ? 0 : daysSince(c.signedAt);
  }

  protected linesSummary(order: ContractOrderResponse): string {
    return order.lines.map((l) => `${formatQuantity(l.quantity)} × ${l.description}`).join(' · ');
  }

  /** Um contrato mudou: troca na lista sem recarregar e avisa o detalhe (a licitação pode ter mudado de situação). */
  protected onContractSaved(updated: ContractResponse): void {
    this.closeDialogs();
    this.contracts.update((list) => {
      const current = list ?? [];
      return current.some((c) => c.id === updated.id)
        ? current.map((c) => (c.id === updated.id ? updated : c))
        : [...current, updated];
    });
    this.changed.emit();
  }

  /** O contrato foi salvo com a leitura da IA: abre os itens lidos para conferir. */
  protected openSuggestedItems(contract: ContractResponse, items: ExtractionItem[]): void {
    this.suggestedItems.set(items);
    this.editingItems.set(contract);
  }

  protected sourceTitle(documentId: string): string | null {
    return this.documents.value()?.find((d) => d.id === documentId)?.title ?? null;
  }

  /** Baixa a versão atual do documento de onde o pedido foi lido. */
  protected downloadSource(documentId: string): void {
    const doc = this.documents.value()?.find((d) => d.id === documentId);
    if (!doc) {
      this.actionError.set('O documento do pedido não está mais na licitação.');
      return;
    }
    const version = doc.versions.find((v) => v.number === doc.currentVersion) ?? doc.versions[0];
    this.documentsApi.downloadDocument(doc.id, doc.currentVersion).subscribe({
      next: (blob) => saveBlob(blob, version?.fileName ?? doc.title),
      error: (err: unknown) =>
        this.actionError.set(err instanceof ApiError ? err.detail : 'Não foi possível baixar.'),
    });
  }

  protected onImported(): void {
    this.contracts.reload();
    this.changed.emit();
  }

  protected removeOrder(contract: ContractResponse, orderId: string): void {
    this.actionError.set(null);
    this.api.removeContractOrder(contract.id, orderId, { version: contract.version }).subscribe({
      next: (updated) => {
        this.confirmingRemoval.set(null);
        this.onContractSaved(updated);
      },
      error: (err: unknown) => {
        this.confirmingRemoval.set(null);
        this.actionError.set(err instanceof ApiError ? err.detail : 'Não foi possível apagar.');
      },
    });
  }

  private closeDialogs(): void {
    this.signing.set(false);
    this.editingContract.set(null);
    this.ordering.set(null);
    this.closing.set(null);
    this.editingItems.set(null);
    this.suggestedItems.set(null);
  }
}
