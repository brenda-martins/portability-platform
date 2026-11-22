import { useEffect, useState, type FormEvent } from 'react';
import { apiFetch } from '../../api/client';
import { PanelHeader } from '../../components/PanelHeader';
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
  const [updateBank, setUpdateBank] = useState('');
  const [updateAgency, setUpdateAgency] = useState('');
  const [updateAccount, setUpdateAccount] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (operation?.receivingAccount) {
      setUpdateBank(operation.receivingAccount.bank);
      setUpdateAgency(operation.receivingAccount.agency);
      setUpdateAccount(operation.receivingAccount.account);
    }
  }, [
    operation?.receivingAccount?.bank,
    operation?.receivingAccount?.agency,
    operation?.receivingAccount?.account,
  ]);

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
          installmentIds: selectedInstallmentIds,
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

  async function handleUpdateAccount(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!operation) return;

    setLoading(true);
    setError(null);
    try {
      const result = await apiFetch<PortabilityOperation>(
        `/portability/${operation.operationId}/receiving-account`,
        {
          method: 'PUT',
          body: JSON.stringify({ bank: updateBank, agency: updateAgency, account: updateAccount }),
        },
      );
      onOperationRegistered(result);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Não foi possível atualizar a conta.');
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
      <PanelHeader
        step={3}
        title="Portabilidade"
        description="Registre a operação e acompanhe a confirmação assíncrona."
      />
      <form onSubmit={handleRegister}>
        <div className="field-grid">
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
        </div>
        <div className="form-actions">
          <button className="button" type="submit" disabled={!authorization || loading}>
            Registrar portabilidade
          </button>
          <button
            className="button button--secondary"
            type="button"
            onClick={handleRefreshStatus}
            disabled={!operation || loading}
          >
            Consultar status
          </button>
          {loading && <span className="hint">Carregando…</span>}
        </div>
      </form>

      {operation?.status === 'CONFIRMED' && (
        <form className="subform" onSubmit={handleUpdateAccount}>
          <h3>Atualizar conta de recebimento</h3>
          <div className="field-grid">
            <div className="field">
              <label htmlFor="update-bank">Novo banco</label>
              <input
                id="update-bank"
                value={updateBank}
                onChange={(event) => setUpdateBank(event.target.value)}
              />
            </div>
            <div className="field">
              <label htmlFor="update-agency">Nova agência</label>
              <input
                id="update-agency"
                value={updateAgency}
                onChange={(event) => setUpdateAgency(event.target.value)}
              />
            </div>
            <div className="field">
              <label htmlFor="update-account">Nova conta</label>
              <input
                id="update-account"
                value={updateAccount}
                onChange={(event) => setUpdateAccount(event.target.value)}
              />
            </div>
          </div>
          <div className="form-actions">
            <button className="button" type="submit" disabled={loading}>
              Atualizar conta
            </button>
            {loading && <span className="hint">Carregando…</span>}
          </div>
        </form>
      )}

      {selectedInstallmentIds.length === 0 && <p className="hint">Selecione parcelas na agenda para registrar.</p>}
      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}
      {operation && (
        <dl className="operation-summary">
          <div className="summary-row">
            <dt>Operação</dt>
            <dd>
              {operation.operationId}{' '}
              <span className={`status status--${operation.status.toLowerCase()}`}>{operation.status}</span>
            </dd>
          </div>
          {operation.protocol && (
            <div className="summary-row">
              <dt>Protocolo</dt>
              <dd>{operation.protocol}</dd>
            </div>
          )}
          {operation.receivingAccount && (
            <div className="summary-row">
              <dt>Conta de recebimento</dt>
              <dd>
                {operation.receivingAccount.bank} / {operation.receivingAccount.agency} /{' '}
                {operation.receivingAccount.account}
              </dd>
            </div>
          )}
        </dl>
      )}
      {operation?.status === 'REJECTED' && operation.rejectionReason && (
        <p className="alert" role="alert">
          Motivo da rejeição: {operation.rejectionReason}
        </p>
      )}
    </section>
  );
}
