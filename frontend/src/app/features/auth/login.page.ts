import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Session } from '../../core/auth/session';
import { ApiError } from '../../core/http/api-error';
import { BrandMark } from '../../shared/ui/brand-mark';
import { Icon } from '../../shared/ui/icon';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, BrandMark, Icon],
  templateUrl: './login.page.html',
  styleUrl: './login.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly session = inject(Session);
  private readonly router = inject(Router);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly submitted = signal(false);

  protected submit(): void {
    this.submitted.set(true);
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    this.session.login(email, password).subscribe({
      next: () => void this.router.navigateByUrl('/'),
      error: (err: unknown) => {
        this.submitting.set(false);
        this.error.set(messageFor(err));
      },
    });
  }

  protected showError(control: 'email' | 'password'): boolean {
    const c = this.form.controls[control];
    return c.invalid && (c.touched || this.submitted());
  }
}

function messageFor(err: unknown): string {
  if (!(err instanceof ApiError)) {
    return 'Algo deu errado. Tente de novo em instantes.';
  }
  switch (err.code) {
    case 'INVALID_CREDENTIALS':
      return 'E-mail ou senha não conferem.';
    case 'ACCOUNT_LOCKED':
    case 'RATE_LIMITED':
    case 'NETWORK_ERROR':
      return err.detail;
    default:
      return 'Não foi possível entrar agora. Tente de novo em instantes.';
  }
}
