import { useState, type FormEvent } from 'react';
import { apiFetch } from '../../api/client';
import { PanelHeader } from '../../components/PanelHeader';
import type { Authorization, AuthorizeRequest } from '../../types';

interface AuthorizationPanelProps {
  onAuthorized: (authorization: Authorization) => void;
}

export function AuthorizationPanel({ onAuthorized }: AuthorizationPanelProps) {
  const [beneficiaryId, setBeneficiaryId] = useState('BEN-123');
  const [agreementId, setAgreementId] = useState('AGR-001');
  const [loading, setLoading] = useState(false);
  const [confirmation, setConfirmation] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    setConfirmation(null);

    try {
      const request: AuthorizeRequest = {
        beneficiaryId: beneficiaryId.trim(),
        agreementId: agreementId.trim(),
      };
      const authorization = await apiFetch<Authorization>('/authorizations', {
        method: 'POST',
        body: JSON.stringify(request),
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
      <PanelHeader
        step={1}
        title="Autorização"
        description="Permita a consulta da agenda deste beneficiário."
      />
      <form onSubmit={handleSubmit}>
        <div className="field-grid">
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
        </div>
        <div className="form-actions">
          <button className="button" type="submit" disabled={loading}>
            Autorizar
          </button>
          {loading && <span className="hint">Carregando…</span>}
        </div>
        <p className="hint">Valores da simulação: BEN-123 e AGR-001. Altere se desejar.</p>
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
