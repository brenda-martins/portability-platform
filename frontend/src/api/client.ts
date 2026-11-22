const API_BASE_URL = import.meta.env.VITE_API_URL ?? '/api';

/** Thin fetch wrapper; feature hooks will be added on top of it. */
export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  });

  if (!response.ok) {
    let message = `Request failed: ${response.status} ${response.statusText}`;
    try {
      const body = (await response.json()) as { message?: unknown };
      if (typeof body.message === 'string' && body.message) {
        message = body.message;
      }
    } catch {
      // Keeps the HTTP status message when the error body is not JSON.
    }
    throw new Error(message);
  }

  return (await response.json()) as T;
}
