import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { ApiError } from './api-error';

describe('ApiError.from', () => {
  it('lê código, detalhe e erros de campo do ProblemDetail', () => {
    const error = ApiError.from(
      new HttpErrorResponse({
        status: 400,
        error: {
          code: 'VALIDATION_ERROR',
          detail: 'Confira os campos destacados.',
          correlationId: 'abc12345',
          errors: [{ field: 'email', message: 'E-mail inválido' }],
        },
      }),
    );
    expect(error.code).toBe('VALIDATION_ERROR');
    expect(error.status).toBe(400);
    expect(error.fieldErrors).toEqual([{ field: 'email', message: 'E-mail inválido' }]);
    expect(error.correlationId).toBe('abc12345');
  });

  it('trata falta de conexão como NETWORK_ERROR', () => {
    expect(ApiError.from(new HttpErrorResponse({ status: 0 })).code).toBe('NETWORK_ERROR');
  });

  it('não confia em corpo que não é ProblemDetail', () => {
    const error = ApiError.from(
      new HttpErrorResponse({ status: 502, error: '<html>Bad gateway</html>' }),
    );
    expect(error.code).toBe('UNEXPECTED_ERROR');
  });
});
