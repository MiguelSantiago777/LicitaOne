import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { Notices } from '../notices/notices';
import { ApiError } from './api-error';

/**
 * Converte HttpErrorResponse em ApiError. O conflito de versão (409) também vira um aviso global, porque a
 * resposta certa é sempre a mesma: recarregar e refazer a alteração.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notices = inject(Notices);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }
      const apiError = ApiError.from(error);
      if (apiError.code === 'VERSION_CONFLICT') {
        notices.show({ tone: 'warning', message: apiError.detail });
      }
      return throwError(() => apiError);
    }),
  );
};
