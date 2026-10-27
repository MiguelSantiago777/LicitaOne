import { HttpContext, HttpContextToken } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, firstValueFrom, map, of, shareReplay, tap } from 'rxjs';
import { AuthService } from '../../api/generated/auth/auth.service';
import type { AccessTokenResponse, UserResponse } from '../../api/generated/model';

/** Marca as chamadas de autenticação, que não levam Bearer nem disparam refresh automático. */
export const AUTH_CALL = new HttpContextToken<boolean>(() => false);

/**
 * Estado da sessão. O access token vive só na memória (nunca em localStorage); o refresh token fica num cookie
 * HttpOnly que o JavaScript não lê. Ao recarregar a página, `restore()` troca o cookie por um token novo.
 */
@Injectable({ providedIn: 'root' })
export class Session {
  private readonly api = inject(AuthService);
  private readonly router = inject(Router);

  private readonly token = signal<string | null>(null);
  private refreshInFlight: Observable<string | null> | null = null;

  readonly user = signal<UserResponse | null>(null);
  readonly isAuthenticated = computed(() => this.token() !== null);

  accessToken(): string | null {
    return this.token();
  }

  login(email: string, password: string): Observable<UserResponse> {
    return this.api.login({ email, password }, authCallOptions()).pipe(
      tap((response) => this.accept(response)),
      map((response) => response.user),
    );
  }

  /** Um único refresh por vez, compartilhado entre as requisições que receberam 401 juntas. */
  refresh(): Observable<string | null> {
    this.refreshInFlight ??= this.api.refresh(authCallOptions()).pipe(
      tap((response) => this.accept(response)),
      map((response) => response.accessToken),
      catchError(() => {
        this.clear();
        return of(null);
      }),
      finalize(() => (this.refreshInFlight = null)),
      shareReplay(1),
    );
    return this.refreshInFlight;
  }

  restore(): Promise<unknown> {
    return firstValueFrom(this.refresh());
  }

  logout(): void {
    this.api.logout(authCallOptions()).subscribe({ error: () => undefined });
    this.endSession();
  }

  /** Sessão perdida (refresh recusado): limpa o estado e volta ao login. */
  endSession(): void {
    this.clear();
    void this.router.navigate(['/login']);
  }

  private accept(response: AccessTokenResponse): void {
    this.token.set(response.accessToken);
    this.user.set(response.user);
  }

  private clear(): void {
    this.token.set(null);
    this.user.set(null);
  }
}

function authCallOptions() {
  return { withCredentials: true, context: new HttpContext().set(AUTH_CALL, true) };
}
