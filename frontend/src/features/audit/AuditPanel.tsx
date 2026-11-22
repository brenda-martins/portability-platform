import { useEffect, useState, type FormEvent } from 'react';
import { apiFetch } from '../../api/client';
import type { AuditEvent, PortabilityOperation } from '../../types';

interface AuditPanelProps {
  operation: PortabilityOperation | null;
}

export function AuditPanel({ operation }: AuditPanelProps) {
  const [operationId, setOperationId] = useState('');
  const [events, setEvents] = useState<AuditEvent[]>([]);
  const [consulted, setConsulted] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (operation) setOperationId(operation.operationId);
  }, [operation]);

  async function handleConsult(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    setEvents([]);
    setConsulted(false);
    setLoading(true);
    setError(null);
    try {
      const trail = await apiFetch<AuditEvent[]>(`/portability/${operationId}/audit`);
      setEvents(trail);
      setConsulted(true);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Não foi possível consultar a auditoria.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="panel">
      <h2>4. Auditoria</h2>
      <form onSubmit={handleConsult}>
        <div className="field">
          <label htmlFor="audit-operation-id">ID da operação</label>
          <input
            id="audit-operation-id"
            value={operationId}
            onChange={(event) => setOperationId(event.target.value)}
          />
        </div>
        <div className="form-actions">
          <button className="button" type="submit" disabled={!operationId || loading}>
            Consultar auditoria
          </button>
          {loading && <span className="hint">Carregando…</span>}
        </div>
      </form>

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}
      {consulted && events.length === 0 && <p className="hint">Nenhum evento registrado.</p>}
      {events.length > 0 && (
        <ul className="event-list">
          {[...events]
            .sort((left, right) => left.occurredAt.localeCompare(right.occurredAt))
            .map((eventItem) => (
              <li key={eventItem.eventId}>
                <span className="event-type">{eventItem.eventType}</span> <span>{eventItem.message}</span>
              </li>
            ))}
        </ul>
      )}
    </section>
  );
}
