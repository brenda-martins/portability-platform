import { ApiError } from './ApiError';

const API_BASE_URL = import.meta.env.VITE_API_URL ?? '/api';

function newCorrelationId(): string {
  const webCrypto = globalThis.crypto;
  if (webCrypto && typeof webCrypto.randomUUID === 'function') {
    return webCrypto.randomUUID();
  }
  return `corr-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

/** Thin fetch wrapper; feature hooks will be added on top of it. */
export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const correlationId = newCorrelationId();
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      'X-Correlation-Id': correlationId,
      ...init?.headers,
    },
  });

  if (!response.ok) {
    let message = `Request failed: ${response.status} ${response.statusText}`;
    let responseCorrelationId: string | undefined;
    let fields: Record<string, string> | undefined;
    try {
      const body = (await response.json()) as {
        message?: unknown;
        correlationId?: unknown;
        fields?: unknown;
      };
      if (typeof body.message === 'string' && body.message) {
        message = body.message;
      }
      if (typeof body.correlationId === 'string' && body.correlationId) {
        responseCorrelationId = body.correlationId;
      }
      if (body.fields && typeof body.fields === 'object') {
        fields = body.fields as Record<string, string>;
      }
    } catch {
      // Keeps the HTTP status message when the error body is not JSON.
    }
    throw new ApiError(message, response.status, responseCorrelationId ?? correlationId, fields);
  }

  return (await response.json()) as T;
}
