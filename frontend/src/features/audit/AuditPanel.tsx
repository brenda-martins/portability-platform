import { useEffect, useState, type FormEvent } from 'react';
import { describeApiError } from '../../api/ApiError';
import { apiFetch } from '../../api/client';
import { PanelHeader } from '../../components/PanelHeader';
import type { AuditEvent, PortabilityOperation } from '../../types';

interface AuditPanelProps {
  operation: PortabilityOperation | null;
}

function eventTypeClass(eventType: string): string {
  if (eventType.includes('REJEITADA') || eventType.includes('FALHA')) {
    return 'event-type event-type--danger';
  }
  if (eventType.includes('CONFIRMADA')) {
    return 'event-type event-type--success';
  }
  return 'event-type';
}

function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(date);
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
      const trail = await apiFetch<AuditEvent[]>(
        `/portability/${encodeURIComponent(operationId.trim())}/audit`,
      );
      setEvents(trail);
      setConsulted(true);
    } catch (cause) {
      setError(describeApiError(cause, 'Não foi possível consultar a auditoria.'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="panel">
      <PanelHeader
        step={4}
        title="Auditoria"
        description="Eventos imutáveis da operação, em ordem de ocorrência."
      />
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
          <button className="button button--secondary" type="submit" disabled={!operationId || loading}>
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
      {consulted && events.length === 0 && <p className="hint hint--block">Nenhum evento registrado.</p>}
      {events.length > 0 && (
        <ul className="event-list">
          {[...events]
            .sort((left, right) => left.occurredAt.localeCompare(right.occurredAt))
            .map((eventItem) => (
              <li key={eventItem.eventId}>
                <span className={eventTypeClass(eventItem.eventType)}>{eventItem.eventType}</span>
                <span>{eventItem.message}</span>
                <time className="event-time" dateTime={eventItem.occurredAt}>
                  {formatDate(eventItem.occurredAt)}
                </time>
              </li>
            ))}
        </ul>
      )}
    </section>
  );
}
