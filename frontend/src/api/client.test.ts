import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from './ApiError';
import { apiFetch } from './client';

function responseWith(status: number, body: unknown) {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: 'Bad Gateway',
    json: async () => body,
  } as unknown as Response;
}

function sentCorrelationId(fetchMock: ReturnType<typeof vi.fn>): string {
  const init = fetchMock.mock.calls[0][1] as RequestInit;
  const headers = init.headers as Record<string, string>;
  return headers['X-Correlation-Id'];
}

describe('apiFetch', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('should send a generated correlationId when a request is made', async () => {
    const fetchMock = vi.fn().mockResolvedValue(responseWith(200, { status: 'ok' }));
    vi.stubGlobal('fetch', fetchMock);

    await apiFetch('/health');

    expect(sentCorrelationId(fetchMock)).toEqual(expect.any(String));
    expect(sentCorrelationId(fetchMock).length).toBeGreaterThan(0);
  });

  it('should use the correlationId returned by the API when the response fails', async () => {
    const fetchMock = vi.fn().mockResolvedValue(responseWith(502, {
      message: 'Central indisponível',
      correlationId: 'corr-123',
    }));
    vi.stubGlobal('fetch', fetchMock);

    const error = await apiFetch('/portability').catch((cause: unknown) => cause) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ message: 'Central indisponível', correlationId: 'corr-123', status: 502 });
  });

  it('should keep the HTTP status message when the error body is not JSON', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 502,
      statusText: 'Bad Gateway',
      json: async () => {
        throw new SyntaxError('Unexpected token');
      },
    } as unknown as Response);
    vi.stubGlobal('fetch', fetchMock);

    const error = await apiFetch('/portability').catch((cause: unknown) => cause) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.message).toBe('Request failed: 502 Bad Gateway');
    expect(error.correlationId).toBe(sentCorrelationId(fetchMock));
  });

  it('should use the sent correlationId when the API does not return one', async () => {
    const fetchMock = vi.fn().mockResolvedValue(responseWith(502, { message: 'Central indisponível' }));
    vi.stubGlobal('fetch', fetchMock);

    const error = await apiFetch('/portability').catch((cause: unknown) => cause) as ApiError;

    expect(error.correlationId).toBe(sentCorrelationId(fetchMock));
  });
});
