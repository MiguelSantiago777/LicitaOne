import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AUTH_CALL, Session } from './session';

const withToken = (req: HttpRequest<unknown>, token: string | null) =>
  token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

/**
 * Coloca o Bearer nas chamadas à API. Se uma chamada voltar 401 (token expirado), renova o token uma vez e
 * repete a chamada; se a renovação falhar, encerra a sessão.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.context.get(AUTH_CALL) || !req.url.startsWith('/api/')) {
    return next(req);
  }
  const session = inject(Session);
  return next(withToken(req, session.accessToken())).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
        return throwError(() => error);
      }
      return session.refresh().pipe(
        switchMap((token) => {
          if (!token) {
            session.endSession();
            return throwError(() => error);
          }
          return next(withToken(req, token));
        }),
      );
    }),
  );
};
