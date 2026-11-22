import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiFetch } from './api/client';
import type { AuditEvent, Authorization, DiscountInstallment, PortabilityOperation } from './types';
import App from './App';

vi.mock('./api/client', () => ({ apiFetch: vi.fn() }));

const mockedApiFetch = vi.mocked(apiFetch);

const authorization: Authorization = {
  authorizationId: 'AUT-1',
  beneficiaryId: 'BEN-123',
  agreementId: 'AGR-001',
  createdAt: '2026-11-22T12:34:56Z',
};

const installments: DiscountInstallment[] = [
  {
    installmentId: 'INS-1',
    agreementId: 'AGR-001',
    amount: 120.5,
    discountDate: '2099-01-10',
    status: 'OPEN',
  },
  {
    installmentId: 'INS-2',
    agreementId: 'AGR-001',
    amount: 80,
    discountDate: '2020-01-10',
    status: 'OPEN',
  },
  {
    installmentId: 'INS-3',
    agreementId: 'AGR-001',
    amount: 60,
    discountDate: '2099-02-10',
    status: 'PORTED',
  },
];

const operation: PortabilityOperation = {
  operationId: 'OPE-1',
  status: 'PENDING',
  protocol: 'PRT-1',
  rejectionReason: null,
  receivingAccount: { bank: '001', agency: '1234', account: '56789-0' },
};

const confirmedOperation: PortabilityOperation = {
  ...operation,
  status: 'CONFIRMED',
};

const updatedOperation: PortabilityOperation = {
  ...confirmedOperation,
  receivingAccount: { bank: '002', agency: '4321', account: '98765-4' },
};

function respondWith(value: unknown) {
  mockedApiFetch.mockImplementation(async () => value as never);
}

function respondByPath(routes: Record<string, unknown>, fallback: unknown = []) {
  mockedApiFetch.mockImplementation(async (path) =>
    (path in routes ? routes[path] : fallback) as never,
  );
}

function fillAuthorizationForm() {
  fireEvent.change(screen.getByLabelText(/id do beneficiário/i), { target: { value: 'BEN-123' } });
  fireEvent.change(screen.getByLabelText(/id do convênio/i), { target: { value: 'AGR-001' } });
}

async function authorize() {
  fillAuthorizationForm();
  fireEvent.click(screen.getByRole('button', { name: /autorizar/i }));
  await screen.findByText(/autorização registrada/i);
}

function fillOperationForm() {
  fireEvent.change(screen.getByLabelText(/credor cessionário/i), {
    target: { value: 'Nova Credora S.A.' },
  });
  fireEvent.change(screen.getByLabelText(/^banco$/i), { target: { value: '001' } });
  fireEvent.change(screen.getByLabelText(/agência/i), { target: { value: '1234' } });
  fireEvent.change(screen.getByLabelText(/^conta$/i), { target: { value: '56789-0' } });
}

async function registerOperationWithStatus(
  statusOperation: PortabilityOperation,
  extraRoutes: Record<string, unknown> = {},
) {
  respondByPath({
    '/authorizations': authorization,
    '/portability': statusOperation,
    ...extraRoutes,
  });
  render(<App />);
  await authorize();
  fillOperationForm();
  fireEvent.click(screen.getByRole('button', { name: /registrar portabilidade/i }));
  await screen.findByText(statusOperation.status);
}

async function waitForUpdateFormToShowBank(bank: string) {
  await waitFor(() => {
    expect((screen.getByLabelText(/novo banco/i) as HTMLInputElement).value).toBe(bank);
  });
}

async function waitForAuditOperationId(value: string) {
  await waitFor(() => {
    expect((screen.getByLabelText(/id da operação/i) as HTMLInputElement).value).toBe(value);
  });
}

describe('App', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('It should render all four panels without placeholders when the screen loads', () => {
    render(<App />);

    expect(screen.getByRole('heading', { name: /portability platform/i })).toBeTruthy();
    expect(screen.getByRole('heading', { name: /autorização/i })).toBeTruthy();
    expect(screen.getByRole('heading', { name: /agenda/i })).toBeTruthy();
    expect(screen.getByRole('heading', { name: /portabilidade/i })).toBeTruthy();
    expect(screen.getByRole('heading', { name: /auditoria/i })).toBeTruthy();
    expect(screen.queryByText(/todo:/i)).toBeNull();
  });

  it('the opt-in must be sent when the beneficiary provides their ID and health plan details', async () => {
    respondWith(authorization);
    render(<App />);

    fillAuthorizationForm();
    fireEvent.click(screen.getByRole('button', { name: /autorizar/i }));

    await waitFor(() => {
      expect(mockedApiFetch).toHaveBeenCalledWith('/authorizations', {
        method: 'POST',
        body: JSON.stringify({ beneficiaryId: 'BEN-123', agreementId: 'AGR-001' }),
      });
    });
  });

  it('should display the API failure when authorization returns an error', async () => {
    mockedApiFetch.mockRejectedValue(new Error('Request failed: 502'));
    render(<App />);

    fillAuthorizationForm();
    fireEvent.click(screen.getByRole('button', { name: /autorizar/i }));

    const alert = await screen.findByRole('alert');
    expect(alert.textContent).toContain('502');
  });

  it('should display the message returned by the API when authorization fails', async () => {
    mockedApiFetch.mockRejectedValue(new Error('Central indisponível'));
    render(<App />);

    fillAuthorizationForm();
    fireEvent.click(screen.getByRole('button', { name: /autorizar/i }));

    const alert = await screen.findByRole('alert');
    expect(alert.textContent).toContain('Central indisponível');
  });

  it('should enable the schedule only after authorization', async () => {
    respondByPath({ '/authorizations': authorization });
    render(<App />);

    expect((screen.getByRole('button', { name: /consultar agenda/i }) as HTMLButtonElement).disabled).toBe(true);

    await authorize();

    expect((screen.getByRole('button', { name: /consultar agenda/i }) as HTMLButtonElement).disabled).toBe(false);
  });

  it('the authorized beneficiary must be submitted when the schedule is consulted', async () => {
    respondByPath({ '/authorizations': authorization });
    render(<App />);
    await authorize();

    respondWith(installments);
    fireEvent.click(screen.getByRole('button', { name: /consultar agenda/i }));

    await waitFor(() => {
      expect(mockedApiFetch).toHaveBeenCalledWith('/beneficiaries/BEN-123/agenda');
    });
  });

  it('it must display the agreement, amount, date, and status of the installments queried', async () => {
    respondByPath({ '/authorizations': authorization });
    render(<App />);
    await authorize();
    respondWith(installments);
    fireEvent.click(screen.getByRole('button', { name: /consultar agenda/i }));
    await screen.findByText(/INS-1/);

    const row = screen.getByText(/INS-1/).closest('tr');
    expect(row?.textContent).toContain('AGR-001');
    expect(row?.textContent).toContain('120,50');
    expect(row?.textContent).toContain('2099-01-10');
    expect(row?.textContent).toContain('OPEN');
  });

  it('should display an empty state when the schedule has no installments', async () => {
    respondByPath({ '/authorizations': authorization });
    render(<App />);
    await authorize();
    respondWith([]);
    fireEvent.click(screen.getByRole('button', { name: /consultar agenda/i }));

    expect(await screen.findByText(/nenhuma parcela futura disponível/i)).toBeTruthy();
  });

  it('should display the API error message when the schedule query fails', async () => {
    respondByPath({ '/authorizations': authorization });
    render(<App />);
    await authorize();
    mockedApiFetch.mockRejectedValueOnce(new Error('Beneficiário não autorizado'));
    fireEvent.click(screen.getByRole('button', { name: /consultar agenda/i }));

    const alert = await screen.findByRole('alert');
    expect(alert.textContent).toContain('Beneficiário não autorizado');
  });

  it('must allow selecting only open and future installments', async () => {
    respondByPath({ '/authorizations': authorization });
    render(<App />);
    await authorize();
    respondWith(installments);
    fireEvent.click(screen.getByRole('button', { name: /consultar agenda/i }));
    await screen.findByText(/INS-1/);

    expect((screen.getByLabelText(/INS-1/) as HTMLInputElement).disabled).toBe(false);
    expect((screen.getByLabelText(/INS-2/) as HTMLInputElement).disabled).toBe(true);
    expect((screen.getByLabelText(/INS-3/) as HTMLInputElement).disabled).toBe(true);
  });

  it('the account, creditor, and selected installments must be submitted when the transaction is recorded', async () => {
    respondByPath({ '/authorizations': authorization });
    render(<App />);
    await authorize();
    respondWith(installments);
    fireEvent.click(screen.getByRole('button', { name: /consultar agenda/i }));
    await screen.findByText(/INS-1/);

    fireEvent.click(screen.getByLabelText(/INS-1/));
    fillOperationForm();

    respondWith(operation);
    fireEvent.click(screen.getByRole('button', { name: /registrar portabilidade/i }));

    await waitFor(() => {
      expect(mockedApiFetch).toHaveBeenCalledWith('/portability', {
        method: 'POST',
        body: JSON.stringify({
          beneficiaryId: 'BEN-123',
          installmentIds: ['INS-1'],
          creditorAssignee: 'Nova Credora S.A.',
          receivingAccount: { bank: '001', agency: '1234', account: '56789-0' },
        }),
      });
    });
  });

  it('should display the API error message when registration fails', async () => {
    respondByPath({ '/authorizations': authorization });
    render(<App />);
    await authorize();
    fillOperationForm();
    mockedApiFetch.mockRejectedValueOnce(new Error('Parcela já portada'));

    fireEvent.click(screen.getByRole('button', { name: /registrar portabilidade/i }));

    const alert = await screen.findByRole('alert');
    expect(alert.textContent).toContain('Parcela já portada');
  });

  it('must display the reason for rejection when the operation is rejected', async () => {
    const rejected: PortabilityOperation = {
      operationId: 'OPE-2',
      status: 'REJECTED',
      protocol: 'PRT-2',
      rejectionReason: 'Convênio bloqueado',
      receivingAccount: { bank: '001', agency: '1234', account: '56789-0' },
    };
    respondByPath({ '/authorizations': authorization, '/portability': rejected });
    render(<App />);

    await authorize();
    fillOperationForm();
    fireEvent.click(screen.getByRole('button', { name: /registrar portabilidade/i }));

    const alert = await screen.findByRole('alert');
    expect(alert.textContent).toContain('Convênio bloqueado');
  });

  it('it should display the events when the audit is consulted', async () => {
    const events: AuditEvent[] = [
      {
        eventId: 'EVT-1',
        operationId: 'OPE-1',
        eventType: 'SOLICITADA',
        message: 'Operação solicitada',
        occurredAt: '2026-01-01T10:00:00Z',
      },
    ];
    mockedApiFetch.mockImplementation(async (path) => {
      if (path === '/authorizations') return authorization as never;
      if (path === '/portability') return operation as never;
      if (path.endsWith('/audit')) return events as never;
      return [] as never;
    });
    render(<App />);

    await authorize();
    fillOperationForm();
    fireEvent.click(screen.getByRole('button', { name: /registrar portabilidade/i }));
    await screen.findByText(/PRT-1/);

    const auditButton = screen.getByRole('button', { name: /consultar auditoria/i }) as HTMLButtonElement;
    await waitFor(() => expect(auditButton.disabled).toBe(false));
    fireEvent.click(auditButton);

    expect(await screen.findByText('Operação solicitada')).toBeTruthy();
  });

  it('should refresh the operation status when consulting status', async () => {
    respondByPath({
      '/authorizations': authorization,
      '/portability': operation,
      '/portability/OPE-1': { ...operation, status: 'CONFIRMED' },
    });
    render(<App />);

    await authorize();
    fillOperationForm();
    fireEvent.click(screen.getByRole('button', { name: /registrar portabilidade/i }));
    await screen.findByText('PRT-1');
    fireEvent.click(screen.getByRole('button', { name: /consultar status/i }));

    expect(await screen.findByText('CONFIRMED')).toBeTruthy();
    expect(mockedApiFetch).toHaveBeenCalledWith('/portability/OPE-1');
  });

  it('should display the rejection reason returned by the status consultation', async () => {
    respondByPath({
      '/authorizations': authorization,
      '/portability': operation,
      '/portability/OPE-1': {
        ...operation,
        status: 'REJECTED',
        rejectionReason: 'Convênio bloqueado',
      },
    });
    render(<App />);

    await authorize();
    fillOperationForm();
    fireEvent.click(screen.getByRole('button', { name: /registrar portabilidade/i }));
    await screen.findByText('PRT-1');
    fireEvent.click(screen.getByRole('button', { name: /consultar status/i }));

    expect(await screen.findByText(/motivo da rejeição: convênio bloqueado/i)).toBeTruthy();
  });

  it('must show the integration failure message and keep the operation pending when the status consultation fails', async () => {
    mockedApiFetch.mockImplementation(async (path) => {
      if (path === '/authorizations') return authorization as never;
      if (path === '/portability') return operation as never;
      if (path === '/portability/OPE-1') throw new Error('Central indisponível');
      return [] as never;
    });
    render(<App />);

    await authorize();
    fillOperationForm();
    fireEvent.click(screen.getByRole('button', { name: /registrar portabilidade/i }));
    await screen.findByText('PRT-1');
    fireEvent.click(screen.getByRole('button', { name: /consultar status/i }));

    const alert = await screen.findByRole('alert');
    expect(alert.textContent).toContain('Central indisponível');
    expect(screen.getByText('PENDING')).toBeTruthy();
  });

  it('must display the integration failure event in the audit trail', async () => {
    const events: AuditEvent[] = [
      {
        eventId: 'EVT-F07',
        operationId: 'OPE-1',
        eventType: 'FALHA_INTEGRACAO',
        message: 'Falha ao consultar status na Central: Central indisponível',
        occurredAt: '2026-11-22T12:35:56Z',
      },
    ];
    mockedApiFetch.mockImplementation(async (path) => {
      if (path === '/authorizations') return authorization as never;
      if (path === '/portability') return operation as never;
      if (path.endsWith('/audit')) return events as never;
      return operation as never;
    });
    render(<App />);

    await authorize();
    fillOperationForm();
    fireEvent.click(screen.getByRole('button', { name: /registrar portabilidade/i }));
    await screen.findByText('PRT-1');
    await waitForAuditOperationId('OPE-1');
    fireEvent.click(screen.getByRole('button', { name: /consultar auditoria/i }));

    expect(await screen.findByText('FALHA_INTEGRACAO')).toBeTruthy();
    expect(screen.getByText(/falha ao consultar status na central: central indisponível/i)).toBeTruthy();
  });

  it('should display an empty state when the audit has no events', async () => {
    mockedApiFetch.mockImplementation(async (path) => {
      if (path === '/authorizations') return authorization as never;
      if (path.endsWith('/audit')) return [] as never;
      return operation as never;
    });
    render(<App />);

    await authorize();
    fireEvent.change(screen.getByLabelText(/id da operação/i), { target: { value: 'OPE-1' } });
    fireEvent.click(screen.getByRole('button', { name: /consultar auditoria/i }));

    expect(await screen.findByText(/nenhum evento registrado/i)).toBeTruthy();
  });

  it('must not offer account update while the operation is pending', async () => {
    await registerOperationWithStatus(operation);

    expect(screen.queryByRole('button', { name: /atualizar conta/i })).toBeNull();
  });

  it('must show the update form filled with the current account when the operation is confirmed', async () => {
    await registerOperationWithStatus(confirmedOperation);

    await waitForUpdateFormToShowBank('001');
    expect((screen.getByLabelText(/nova agência/i) as HTMLInputElement).value).toBe('1234');
    expect((screen.getByLabelText(/nova conta/i) as HTMLInputElement).value).toBe('56789-0');
  });

  it('must send the new account and display it after the update', async () => {
    await registerOperationWithStatus(confirmedOperation, {
      '/portability/OPE-1/receiving-account': updatedOperation,
    });
    await waitForUpdateFormToShowBank('001');

    fireEvent.change(screen.getByLabelText(/novo banco/i), { target: { value: '002' } });
    fireEvent.change(screen.getByLabelText(/nova agência/i), { target: { value: '4321' } });
    fireEvent.change(screen.getByLabelText(/nova conta/i), { target: { value: '98765-4' } });
    fireEvent.click(screen.getByRole('button', { name: /atualizar conta/i }));

    await waitFor(() => {
      expect(mockedApiFetch).toHaveBeenCalledWith('/portability/OPE-1/receiving-account', {
        method: 'PUT',
        body: JSON.stringify({ bank: '002', agency: '4321', account: '98765-4' }),
      });
    });
    expect(await screen.findByText(/98765-4/)).toBeTruthy();
  });

  it('must display the API error message when the account update fails', async () => {
    mockedApiFetch.mockImplementation(async (path) => {
      if (path === '/authorizations') return authorization as never;
      if (path === '/portability') return confirmedOperation as never;
      if (path === '/portability/OPE-1/receiving-account') {
        throw new Error('Request failed: 502');
      }
      return [] as never;
    });
    render(<App />);

    await authorize();
    fillOperationForm();
    fireEvent.click(screen.getByRole('button', { name: /registrar portabilidade/i }));
    await screen.findByText('CONFIRMED');
    await waitForUpdateFormToShowBank('001');

    fireEvent.change(screen.getByLabelText(/nova conta/i), { target: { value: '98765-4' } });
    fireEvent.click(screen.getByRole('button', { name: /atualizar conta/i }));

    const alert = await screen.findByRole('alert');
    expect(alert.textContent).toContain('502');
  });

  it('must consult the audit using the registered operation id', async () => {
    await registerOperationWithStatus(operation, { '/portability/OPE-1/audit': [] });
    await waitForAuditOperationId('OPE-1');

    fireEvent.click(screen.getByRole('button', { name: /consultar auditoria/i }));

    await waitFor(() => {
      expect(mockedApiFetch).toHaveBeenCalledWith('/portability/OPE-1/audit');
    });
  });

  it('must display the API error message when the audit consultation fails', async () => {
    await registerOperationWithStatus(operation);
    await waitForAuditOperationId('OPE-1');
    mockedApiFetch.mockImplementation(async (path) => {
      if (path.endsWith('/audit')) throw new Error('Operação não encontrada');
      return [] as never;
    });

    fireEvent.click(screen.getByRole('button', { name: /consultar auditoria/i }));

    const alert = await screen.findByRole('alert');
    expect(alert.textContent).toContain('Operação não encontrada');
  });

  it('must clear the previous trail when a new audit consultation fails', async () => {
    const events: AuditEvent[] = [
      {
        eventId: 'EVT-1',
        operationId: 'OPE-1',
        eventType: 'SOLICITADA',
        message: 'Trilha antiga',
        occurredAt: '2026-01-01T10:00:00Z',
      },
    ];
    await registerOperationWithStatus(operation, { '/portability/OPE-1/audit': events });
    await waitForAuditOperationId('OPE-1');
    fireEvent.click(screen.getByRole('button', { name: /consultar auditoria/i }));
    expect(await screen.findByText('Trilha antiga')).toBeTruthy();

    mockedApiFetch.mockImplementation(async (path) => {
      if (path.endsWith('/audit')) throw new Error('Operação não encontrada');
      return [] as never;
    });
    fireEvent.change(screen.getByLabelText(/id da operação/i), { target: { value: 'OPE-2' } });
    fireEvent.click(screen.getByRole('button', { name: /consultar auditoria/i }));

    expect(await screen.findByRole('alert')).toBeTruthy();
    await waitFor(() => expect(screen.queryByText('Trilha antiga')).toBeNull());
  });
});
