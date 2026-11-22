import { Layout } from './components/Layout';
import { AgendaPanel } from './features/agenda/AgendaPanel';
import { AuditPanel } from './features/audit/AuditPanel';
import { AuthorizationPanel } from './features/authorization/AuthorizationPanel';
import { PortabilityPanel } from './features/portability/PortabilityPanel';

export default function App() {
  return (
    <Layout>
      <AuthorizationPanel />
      <AgendaPanel />
      <PortabilityPanel />
      <AuditPanel />
    </Layout>
  );
}
