import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { LoginPage } from './login.page';

describe('LoginPage', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  function render() {
    const fixture = TestBed.createComponent(LoginPage);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  function type(el: HTMLElement, selector: string, value: string) {
    const input = el.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('mostra os erros de campo sem chamar a API quando o formulário está vazio', async () => {
    const { fixture, el } = render();
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();

    expect(el.querySelector('#email-error')?.textContent).toContain('Informe o e-mail');
    expect(el.querySelector('#password-error')?.textContent).toContain('Informe a senha');
    backend.expectNone('/api/v1/auth/login');
  });

  it('avisa quando e-mail ou senha não conferem', async () => {
    const { fixture, el } = render();
    type(el, '#email', 'edwin@shema.local');
    type(el, '#password', 'errada');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();

    backend
      .expectOne('/api/v1/auth/login')
      .flush(
        { code: 'INVALID_CREDENTIALS', detail: 'E-mail ou senha inválidos' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await fixture.whenStable();

    expect(el.querySelector('[role=alert]')?.textContent).toContain('E-mail ou senha não conferem');
    expect(el.querySelector<HTMLButtonElement>('button[type=submit]')!.disabled).toBe(false);
  });
});
