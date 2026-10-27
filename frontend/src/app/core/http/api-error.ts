import { HttpErrorResponse } from '@angular/common/http';

export interface FieldError {
  field: string;
  message: string;
}

/** Erro da API já traduzido do ProblemDetail (RFC 7807). As telas decidem pelo `code`, nunca pelo texto. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly detail: string,
    readonly fieldErrors: FieldError[] = [],
    readonly correlationId?: string,
  ) {
    super(detail);
    this.name = 'ApiError';
  }

  static from(response: HttpErrorResponse): ApiError {
    if (response.status === 0) {
      return new ApiError(
        0,
        'NETWORK_ERROR',
        'Sem conexão com o servidor. Confira a internet e tente de novo.',
      );
    }
    const body = response.error as Partial<Record<string, unknown>> | null;
    const isProblem = body !== null && typeof body === 'object' && typeof body['code'] === 'string';
    if (!isProblem) {
      return new ApiError(
        response.status,
        'UNEXPECTED_ERROR',
        'Algo deu errado. Tente de novo em instantes.',
      );
    }
    return new ApiError(
      response.status,
      body['code'] as string,
      typeof body['detail'] === 'string' ? body['detail'] : 'Algo deu errado.',
      Array.isArray(body['errors']) ? (body['errors'] as FieldError[]) : [],
      typeof body['correlationId'] === 'string' ? body['correlationId'] : undefined,
    );
  }
}
