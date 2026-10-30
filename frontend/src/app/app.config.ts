import { DOCUMENT, registerLocaleData } from '@angular/common';
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
import {
  ActivatedRouteSnapshot,
  provideRouter,
  withComponentInputBinding,
  withViewTransitions,
} from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { Session } from './core/auth/session';
import { correlationInterceptor } from './core/http/correlation.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';

registerLocaleData(localePt, 'pt-BR');

/** Caminho da rota sem a query: trocar aba, filtro ou busca não é trocar de página. */
function routePath(route: ActivatedRouteSnapshot | null): string {
  const parts: string[] = [];
  for (let r = route; r; r = r.firstChild) parts.push(...r.url.map((s) => s.path));
  return parts.join('/');
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      withComponentInputBinding(),
      // Troca de página animada (styles.scss); só quando muda a rota e o sistema não pede menos movimento
      withViewTransitions({
        skipInitialTransition: true,
        onViewTransitionCreated: ({ transition, from, to }) => {
          const reduced = !globalThis.matchMedia?.('(prefers-reduced-motion: no-preference)')
            .matches;
          if (reduced || routePath(from) === routePath(to)) {
            // pular rejeita a promessa ready com AbortError: é esperado, não é erro
            transition.ready.catch(() => undefined);
            transition.skipTransition();
            return;
          }
          // Os nomes de transição criam contexto de empilhamento (prenderiam a gaveta sob a barra superior): só
          // valem enquanto a troca acontece
          const root = inject(DOCUMENT).documentElement;
          root.classList.add('page-transition');
          transition.finished.finally(() => root.classList.remove('page-transition'));
        },
      }),
    ),
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
