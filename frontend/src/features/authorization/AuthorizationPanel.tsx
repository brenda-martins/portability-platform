import { useState, type FormEvent } from 'react';
import { apiFetch } from '../../api/client';
import type { Authorization } from '../../types';

interface AuthorizationPanelProps {
  onAuthorized: (authorization: Authorization) => void;
}

export function AuthorizationPanel({ onAuthorized }: AuthorizationPanelProps) {
  const [beneficiaryId, setBeneficiaryId] = useState('');
  const [agreementId, setAgreementId] = useState('');
  const [loading, setLoading] = useState(false);
  const [confirmation, setConfirmation] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    setConfirmation(null);

    try {
      const authorization = await apiFetch<Authorization>('/authorizations', {
        method: 'POST',
        body: JSON.stringify({ beneficiaryId, agreementId }),
      });
      onAuthorized(authorization);
      setConfirmation(`Autorização registrada: ${authorization.authorizationId}`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Não foi possível registrar a autorização.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="panel">
      <h2>1. Autorização (opt-in)</h2>
      <form onSubmit={handleSubmit}>
        <div className="field">
          <label htmlFor="beneficiary-id">ID do beneficiário</label>
          <input
            id="beneficiary-id"
            value={beneficiaryId}
            onChange={(event) => setBeneficiaryId(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="agreement-id">ID do convênio</label>
          <input
            id="agreement-id"
            value={agreementId}
            onChange={(event) => setAgreementId(event.target.value)}
          />
        </div>
        <div className="form-actions">
          <button className="button" type="submit" disabled={loading}>
            Autorizar
          </button>
          {loading && <span className="hint">Carregando…</span>}
        </div>
      </form>
      {confirmation && <p className="status">{confirmation}</p>}
      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}
    </section>
  );
}
