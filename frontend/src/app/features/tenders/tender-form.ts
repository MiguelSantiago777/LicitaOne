import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TendersService } from '../../api/generated/tenders/tenders.service';
import { UsersService } from '../../api/generated/users/users.service';
import type { AgencyRefResponse, TenderRequest, TenderResponse } from '../../api/generated/model';
import { Session } from '../../core/auth/session';
import { ApiError } from '../../core/http/api-error';
import {
  formatBrlInput,
  fromLocalDateTimeInput,
  parseBrl,
  toLocalDateTimeInput,
  todayIso,
} from '../../shared/format';
import { Panel } from '../../shared/ui/panel';
import { AgencyPicker } from './agency-picker';
import { MODALITIES } from './tender-meta';

/** Gaveta de cadastro e edição de licitação. Sem `tender`, cadastra; com `tender`, edita. */
@Component({
  selector: 'app-tender-form',
  imports: [ReactiveFormsModule, Panel, AgencyPicker],
  templateUrl: './tender-form.html',
  styleUrl: './tender-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TenderForm {
  private readonly tendersApi = inject(TendersService);
  private readonly usersApi = inject(UsersService);
  private readonly session = inject(Session);

  readonly open = input(false);
  readonly tender = input<TenderResponse | null>(null);
  readonly closed = output<void>();
  readonly saved = output<TenderResponse>();

  protected readonly modalities = MODALITIES;
  protected readonly today = todayIso();
  protected readonly agency = signal<AgencyRefResponse | null>(null);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly serverFieldErrors = signal<Record<string, string>>({});
  protected readonly submitted = signal(false);
  protected readonly isEdit = computed(() => this.tender() !== null);

  protected readonly owners = rxResource({ stream: () => this.usersApi.listUserOptions() });
  protected readonly suggestions = rxResource({
    stream: () => this.tendersApi.tenderSuggestions(),
  });

  protected readonly form = inject(NonNullableFormBuilder).group({
    initialStatus: ['VENCIDA' as 'VENCIDA' | 'EM_DISPUTA'],
    wonAt: [todayIso()],
    modality: ['PREGAO_ELETRONICO'],
    uasg: [''],
    processNumber: [''],
    portal: [''],
    sessionAt: [''],
    object: ['', [Validators.required, Validators.maxLength(2000)]],
    materialType: ['', [Validators.required, Validators.maxLength(120)]],
    itemsQuantity: [''],
    totalValue: ['', Validators.required],
    ownerId: [''],
    notes: [''],
  });

  constructor() {
    // Ao abrir, preenche com a licitação (edição) ou limpa (cadastro)
    effect(() => {
      if (!this.open()) return;
      const t = this.tender();
      this.error.set(null);
      this.serverFieldErrors.set({});
      this.submitted.set(false);
      if (t) {
        this.agency.set(t.agency);
        this.form.reset({
          initialStatus: 'VENCIDA',
          wonAt: t.wonAt ?? todayIso(),
          modality: t.modality,
          uasg: t.uasg ?? '',
          processNumber: t.processNumber ?? '',
          portal: t.portal ?? '',
          sessionAt: toLocalDateTimeInput(t.sessionAt),
          object: t.object,
          materialType: t.materialType,
          itemsQuantity: t.itemsQuantity == null ? '' : String(t.itemsQuantity),
          totalValue: formatBrlInput(t.totalValue),
          ownerId: t.owner?.id ?? '',
          notes: t.notes ?? '',
        });
      } else {
        this.agency.set(null);
        this.form.reset({
          initialStatus: 'VENCIDA',
          wonAt: todayIso(),
          modality: 'PREGAO_ELETRONICO',
          ownerId: this.session.user()?.role === 'ADMIN' ? '' : (this.session.user()?.id ?? ''),
        });
      }
    });
  }

  protected showError(name: keyof typeof this.form.controls): boolean {
    const c = this.form.controls[name];
    return (c.invalid && (c.touched || this.submitted())) || !!this.serverFieldErrors()[name];
  }

  protected fieldError(name: string): string | undefined {
    return this.serverFieldErrors()[name];
  }

  protected formatValue(): void {
    const parsed = parseBrl(this.form.controls.totalValue.value);
    if (parsed != null) this.form.controls.totalValue.setValue(formatBrlInput(parsed));
  }

  protected submit(): void {
    this.submitted.set(true);
    this.error.set(null);
    this.serverFieldErrors.set({});
    const v = this.form.getRawValue();
    const total = parseBrl(v.totalValue);
    const errors: Record<string, string> = {};
    if (!this.agency()) errors['agencyId'] = 'Escolha o órgão.';
    if (v.totalValue && total == null) errors['totalValue'] = 'Valor inválido. Exemplo: 174.000,00';
    if (!v.uasg.trim() && !v.processNumber.trim())
      errors['processNumber'] = 'Informe a UASG, o número do processo ou os dois.';
    if (!this.isEdit() && v.initialStatus === 'VENCIDA' && !v.wonAt)
      errors['wonAt'] = 'Informe a data da vitória.';
    if (Object.keys(errors).length || this.form.invalid) {
      this.serverFieldErrors.set(errors);
      this.form.markAllAsTouched();
      this.error.set('Confira os campos destacados.');
      return;
    }
    const body: TenderRequest = {
      agencyId: this.agency()!.id,
      modality: v.modality as TenderRequest['modality'],
      uasg: v.uasg.trim() || undefined,
      processNumber: v.processNumber.trim() || undefined,
      portal: v.portal.trim() || undefined,
      sessionAt: fromLocalDateTimeInput(v.sessionAt),
      object: v.object.trim(),
      materialType: v.materialType.trim(),
      itemsQuantity: v.itemsQuantity === '' ? undefined : Number(v.itemsQuantity),
      totalValue: total!,
      ownerId: v.ownerId || undefined,
      notes: v.notes.trim() || undefined,
    };
    this.saving.set(true);
    const t = this.tender();
    const request = t
      ? this.tendersApi.updateTender(t.id, { version: t.version, tender: body })
      : this.tendersApi.createTender({
          initialStatus: v.initialStatus,
          wonAt: v.initialStatus === 'VENCIDA' ? v.wonAt : undefined,
          tender: body,
        });
    request.subscribe({
      next: (result) => {
        this.saving.set(false);
        this.saved.emit(result);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        if (err instanceof ApiError) {
          const fields: Record<string, string> = {};
          for (const f of err.fieldErrors) fields[f.field.replace(/^tender\./, '')] = f.message;
          this.serverFieldErrors.set(fields);
          this.error.set(err.detail);
        } else {
          this.error.set('Não foi possível salvar. Tente de novo.');
        }
      },
    });
  }
}
