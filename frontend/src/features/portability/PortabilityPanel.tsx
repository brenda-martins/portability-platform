import { useState, type FormEvent } from 'react';
import { apiFetch } from '../../api/client';
import type { Authorization, PortabilityOperation } from '../../types';

interface PortabilityPanelProps {
  authorization: Authorization | null;
  selectedInstallmentIds: string[];
  operation: PortabilityOperation | null;
  onOperationRegistered: (operation: PortabilityOperation) => void;
}

export function PortabilityPanel({
  authorization,
  selectedInstallmentIds,
  operation,
  onOperationRegistered,
}: PortabilityPanelProps) {
  const [creditorAssignee, setCreditorAssignee] = useState('');
  const [bank, setBank] = useState('');
  const [agency, setAgency] = useState('');
  const [account, setAccount] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleRegister(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!authorization) return;

    setLoading(true);
    setError(null);
    try {
      const result = await apiFetch<PortabilityOperation>('/portability', {
        method: 'POST',
        body: JSON.stringify({
          beneficiaryId: authorization.beneficiaryId,
          installments: selectedInstallmentIds,
          creditorAssignee,
          receivingAccount: { bank, agency, account },
        }),
      });
      onOperationRegistered(result);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Não foi possível registrar a operação.');
    } finally {
      setLoading(false);
    }
  }

  async function handleRefreshStatus() {
    if (!operation) return;

    setLoading(true);
    setError(null);
    try {
      const result = await apiFetch<PortabilityOperation>(`/portability/${operation.operationId}`);
      onOperationRegistered(result);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Não foi possível consultar o status.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="panel">
      <h2>3. Portabilidade</h2>
      <form onSubmit={handleRegister}>
        <div className="field">
          <label htmlFor="creditor-assignee">Credor cessionário</label>
          <input
            id="creditor-assignee"
            value={creditorAssignee}
            onChange={(event) => setCreditorAssignee(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="bank">Banco</label>
          <input id="bank" value={bank} onChange={(event) => setBank(event.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="agency">Agência</label>
          <input id="agency" value={agency} onChange={(event) => setAgency(event.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="account">Conta</label>
          <input id="account" value={account} onChange={(event) => setAccount(event.target.value)} />
        </div>
        <div className="form-actions">
          <button className="button" type="submit" disabled={!authorization || loading}>
            Registrar portabilidade
          </button>
          <button className="button" type="button" onClick={handleRefreshStatus} disabled={!operation || loading}>
            Consultar status
          </button>
          {loading && <span className="hint">Carregando…</span>}
        </div>
      </form>

      {selectedInstallmentIds.length === 0 && <p className="hint">Selecione parcelas na agenda para registrar.</p>}
      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}
      {operation && (
        <p>
          Operação {operation.operationId}:{' '}
          <span className={`status${operation.status === 'REJECTED' ? ' status--rejected' : ''}`}>
            {operation.status}
          </span>
        </p>
      )}
      {operation?.protocol && (
        <p>
          Protocolo: <strong>{operation.protocol}</strong>
        </p>
      )}
      {operation?.status === 'REJECTED' && operation.rejectionReason && (
        <p className="alert" role="alert">
          Motivo da rejeição: {operation.rejectionReason}
        </p>
      )}
    </section>
  );
}
