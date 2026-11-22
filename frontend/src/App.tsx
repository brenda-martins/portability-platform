import { useState } from 'react';
import { Layout } from './components/Layout';
import { AgendaPanel } from './features/agenda/AgendaPanel';
import { AuditPanel } from './features/audit/AuditPanel';
import { AuthorizationPanel } from './features/authorization/AuthorizationPanel';
import { PortabilityPanel } from './features/portability/PortabilityPanel';
import type { Authorization, DiscountInstallment, PortabilityOperation } from './types';

export default function App() {
  const [authorization, setAuthorization] = useState<Authorization | null>(null);
  const [installments, setInstallments] = useState<DiscountInstallment[]>([]);
  const [selectedInstallmentIds, setSelectedInstallmentIds] = useState<string[]>([]);
  const [operation, setOperation] = useState<PortabilityOperation | null>(null);

  function handleInstallmentsLoaded(items: DiscountInstallment[]) {
    setInstallments(items);
    setSelectedInstallmentIds([]);
  }

  return (
    <Layout>
      <AuthorizationPanel onAuthorized={setAuthorization} />
      <AgendaPanel
        authorization={authorization}
        installments={installments}
        selectedInstallmentIds={selectedInstallmentIds}
        onInstallmentsLoaded={handleInstallmentsLoaded}
        onSelectionChange={setSelectedInstallmentIds}
      />
      <PortabilityPanel
        authorization={authorization}
        selectedInstallmentIds={selectedInstallmentIds}
        operation={operation}
        onOperationRegistered={setOperation}
      />
      <AuditPanel operation={operation} />
    </Layout>
  );
}
