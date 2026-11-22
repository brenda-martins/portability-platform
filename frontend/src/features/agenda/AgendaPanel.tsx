import { useState, type FormEvent } from 'react';
import { describeApiError } from '../../api/ApiError';
import { apiFetch } from '../../api/client';
import { PanelHeader } from '../../components/PanelHeader';
import type { Authorization, DiscountInstallment } from '../../types';

interface AgendaPanelProps {
  authorization: Authorization | null;
  installments: DiscountInstallment[];
  selectedInstallmentIds: string[];
  onInstallmentsLoaded: (installments: DiscountInstallment[]) => void;
  onSelectionChange: (installmentIds: string[]) => void;
}

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

function isEligible(installment: DiscountInstallment): boolean {
  return installment.status === 'OPEN' && new Date(installment.discountDate).getTime() > Date.now();
}

export function AgendaPanel({
  authorization,
  installments,
  selectedInstallmentIds,
  onInstallmentsLoaded,
  onSelectionChange,
}: AgendaPanelProps) {
  const [loading, setLoading] = useState(false);
  const [consulted, setConsulted] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleConsult(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!authorization) return;

    setLoading(true);
    setError(null);
    try {
      const items = await apiFetch<DiscountInstallment[]>(
        `/beneficiaries/${encodeURIComponent(authorization.beneficiaryId)}/agenda`,
      );
      onInstallmentsLoaded(items);
      setConsulted(true);
    } catch (cause) {
      setError(describeApiError(cause, 'Não foi possível consultar a agenda.'));
    } finally {
      setLoading(false);
    }
  }

  function toggle(installmentId: string) {
    onSelectionChange(
      selectedInstallmentIds.includes(installmentId)
        ? selectedInstallmentIds.filter((id) => id !== installmentId)
        : [...selectedInstallmentIds, installmentId],
    );
  }

  return (
    <section className="panel">
      <PanelHeader
        step={2}
        title="Agenda de descontos"
        description="Parcelas futuras devolvidas pela Central de Convênios."
      />
      <form onSubmit={handleConsult}>
        <div className="form-actions">
          <button className="button button--secondary" type="submit" disabled={!authorization || loading}>
            Consultar agenda
          </button>
          {!authorization && <span className="hint">Autorize o beneficiário para consultar a agenda.</span>}
          {loading && <span className="hint">Carregando…</span>}
        </div>
      </form>

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}
      {consulted && installments.length === 0 && (
        <p className="hint hint--block">
          Nenhuma parcela futura disponível para {authorization?.beneficiaryId}. A simulação possui dados
          apenas para BEN-123.
        </p>
      )}

      {installments.length > 0 && (
        <table className="installments">
          <thead>
            <tr>
              <th scope="col">Selecionar</th>
              <th scope="col">Parcela</th>
              <th scope="col">Convênio</th>
              <th scope="col">Data</th>
              <th scope="col">Valor</th>
              <th scope="col">Status</th>
            </tr>
          </thead>
          <tbody>
            {installments.map((installment) => (
              <tr key={installment.installmentId}>
                <td>
                  <input
                    type="checkbox"
                    aria-label={installment.installmentId}
                    disabled={!isEligible(installment)}
                    checked={selectedInstallmentIds.includes(installment.installmentId)}
                    onChange={() => toggle(installment.installmentId)}
                  />
                </td>
                <td>{installment.installmentId}</td>
                <td>{installment.agreementId}</td>
                <td>{installment.discountDate}</td>
                <td>{currency.format(installment.amount)}</td>
                <td>{installment.status}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
