export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly correlationId?: string,
    readonly fields?: Record<string, string>,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export function describeApiError(cause: unknown, fallback: string): string {
  if (cause instanceof ApiError && cause.correlationId) {
    return `${cause.message} (correlação: ${cause.correlationId})`;
  }
  return cause instanceof Error ? cause.message : fallback;
}
