import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { errorInterceptor } from '../http/error.interceptor';
import { authInterceptor } from './auth.interceptor';
import { Session } from './session';

const tokenResponse = (accessToken: string) => ({
  accessToken,
  tokenType: 'Bearer',
  expiresIn: 900,
  user: {
    id: '1',
    name: 'Edwin Lima',
    email: 'edwin@shema.local',
    role: 'SALES',
    roleLabel: 'Comercial',
    initials: 'EL',
  },
});

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let session: Session;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor, authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    session = TestBed.inject(Session);
  });

  function loginAs(token: string): void {
    session.login('edwin@shema.local', 'x').subscribe();
    backend.expectOne('/api/v1/auth/login').flush(tokenResponse(token));
  }

  it('envia o Bearer nas chamadas à API, mas não no login', () => {
    session.login('edwin@shema.local', 'x').subscribe();
    const login = backend.expectOne('/api/v1/auth/login');
    expect(login.request.headers.has('Authorization')).toBe(false);
    expect(login.request.withCredentials).toBe(true);
    login.flush(tokenResponse('t1'));

    http.get('/api/v1/me').subscribe();
    expect(backend.expectOne('/api/v1/me').request.headers.get('Authorization')).toBe('Bearer t1');
  });

  it('renova o token uma vez quando a API responde 401 e repete a chamada', () => {
    loginAs('velho');
    let body: unknown;
    http.get('/api/v1/me').subscribe((b) => (body = b));

    backend
      .expectOne('/api/v1/me')
      .flush({ code: 'UNAUTHENTICATED' }, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/v1/auth/refresh').flush(tokenResponse('novo'));
    const retry = backend.expectOne('/api/v1/me');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer novo');
    retry.flush({ ok: true });

    expect(body).toEqual({ ok: true });
  });

  it('encerra a sessão quando o refresh falha', () => {
    loginAs('velho');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    let failed = false;
    http.get('/api/v1/me').subscribe({ error: () => (failed = true) });

    backend
      .expectOne('/api/v1/me')
      .flush({ code: 'UNAUTHENTICATED' }, { status: 401, statusText: 'Unauthorized' });
    backend
      .expectOne('/api/v1/auth/refresh')
      .flush({ code: 'INVALID_REFRESH_TOKEN' }, { status: 401, statusText: 'Unauthorized' });

    expect(failed).toBe(true);
    expect(session.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
});
