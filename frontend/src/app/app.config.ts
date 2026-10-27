import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localePt from '@angular/common/locales/pt';
import {
  ApplicationConfig,
  DEFAULT_CURRENCY_CODE,
  LOCALE_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { Session } from './core/auth/session';
import { correlationInterceptor } from './core/http/correlation.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';

registerLocaleData(localePt, 'pt-BR');

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    // Ordem importa: o errorInterceptor fica por fora do authInterceptor, para que este ainda receba o
    // HttpErrorResponse 401 cru e consiga renovar o token antes de o erro virar ApiError
    provideHttpClient(
      withInterceptors([correlationInterceptor, errorInterceptor, authInterceptor]),
    ),
    { provide: LOCALE_ID, useValue: 'pt-BR' },
    { provide: DEFAULT_CURRENCY_CODE, useValue: 'BRL' },
    // Tenta retomar a sessão pelo cookie de refresh antes da primeira navegação
    provideAppInitializer(() => inject(Session).restore()),
  ],
};
