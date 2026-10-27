import { HttpInterceptorFn } from '@angular/common/http';

/** Cada requisição leva um X-Correlation-Id, que o backend propaga para os logs e para a mensageria. */
export const correlationInterceptor: HttpInterceptorFn = (req, next) =>
  next(req.clone({ setHeaders: { 'X-Correlation-Id': crypto.randomUUID() } }));
