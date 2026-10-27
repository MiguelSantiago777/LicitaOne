import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { Session } from './session';

export const authGuard: CanMatchFn = () =>
  inject(Session).isAuthenticated() || inject(Router).createUrlTree(['/login']);

export const guestGuard: CanMatchFn = () =>
  !inject(Session).isAuthenticated() || inject(Router).createUrlTree(['/']);
