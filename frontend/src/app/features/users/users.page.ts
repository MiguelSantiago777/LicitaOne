import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { UsersService } from '../../api/generated/users/users.service';
import type { UserResponse } from '../../api/generated/model';
import { ApiError } from '../../core/http/api-error';
import { Session } from '../../core/auth/session';
import { Notices } from '../../core/notices/notices';
import { Avatar } from '../../shared/ui/avatar';
import { Icon } from '../../shared/ui/icon';
import { Panel } from '../../shared/ui/panel';

type Role = 'ADMIN' | 'SALES' | 'VIEWER';

@Component({
  selector: 'app-users-page',
  imports: [DatePipe, FormsModule, Avatar, Icon, Panel],
  templateUrl: './users.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .form {
      display: grid;
      gap: 14px;
    }
    .hint {
      color: var(--text-3);
      font-size: 12px;
    }
    .error {
      color: var(--red);
      font-weight: 500;
    }
    .actions {
      display: flex;
      gap: 6px;
      justify-content: flex-end;
    }
    .switch {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .switch input {
      width: 16px;
      height: 16px;
      accent-color: var(--brand);
    }
  `,
})
export class UsersPage {
  private readonly api = inject(UsersService);
  private readonly notices = inject(Notices);
  protected readonly session = inject(Session);

  protected readonly roles: { value: Role; label: string; hint: string }[] = [
    { value: 'SALES', label: 'Comercial', hint: 'Cadastra e altera as próprias licitações' },
    { value: 'ADMIN', label: 'Administrador', hint: 'Tudo, inclusive usuários' },
    { value: 'VIEWER', label: 'Leitura', hint: 'Só consulta' },
  ];

  protected readonly list = rxResource({ stream: () => this.api.listUsers() });
  protected readonly mode = signal<'create' | 'edit' | 'password' | null>(null);
  protected readonly current = signal<UserResponse | null>(null);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected form = { name: '', email: '', role: 'SALES' as Role, password: '', active: true };

  protected openCreate(): void {
    this.form = { name: '', email: '', role: 'SALES', password: '', active: true };
    this.current.set(null);
    this.error.set(null);
    this.mode.set('create');
  }

  protected openEdit(u: UserResponse): void {
    this.form = { name: u.name, email: u.email, role: u.role, password: '', active: u.active };
    this.current.set(u);
    this.error.set(null);
    this.mode.set('edit');
  }

  protected openPassword(u: UserResponse): void {
    this.form = { ...this.form, password: '' };
    this.current.set(u);
    this.error.set(null);
    this.mode.set('password');
  }

  protected save(): void {
    this.saving.set(true);
    this.error.set(null);
    const u = this.current();
    const done = (message: string) => () => {
      this.saving.set(false);
      this.mode.set(null);
      this.list.reload();
      this.notices.show({ tone: 'info', message });
    };
    const fail = (err: unknown) => {
      this.saving.set(false);
      this.error.set(
        err instanceof ApiError
          ? (err.fieldErrors[0]?.message ?? err.detail)
          : 'Não foi possível salvar.',
      );
    };
    switch (this.mode()) {
      case 'create':
        this.api
          .createUser({
            name: this.form.name,
            email: this.form.email,
            role: this.form.role,
            password: this.form.password,
          })
          .subscribe({ next: done('Usuário cadastrado.'), error: fail });
        break;
      case 'edit':
        this.api
          .updateUser(u!.id, {
            name: this.form.name,
            role: this.form.role,
            active: this.form.active,
          })
          .subscribe({ next: done('Usuário atualizado.'), error: fail });
        break;
      case 'password':
        this.api
          .resetUserPassword(u!.id, { password: this.form.password })
          .subscribe({ next: done('Senha definida.'), error: fail });
        break;
    }
  }

  protected title(): string {
    return { create: 'Novo usuário', edit: 'Editar usuário', password: 'Definir nova senha' }[
      this.mode() ?? 'create'
    ];
  }
}
