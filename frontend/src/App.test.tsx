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

describe('App', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('deve renderizar os quatro painéis sem placeholders quando a tela carrega', () => {
    render(<App />);

    expect(screen.getByRole('heading', { name: /portability platform/i })).toBeTruthy();
    expect(screen.getByRole('heading', { name: /autorização/i })).toBeTruthy();
    expect(screen.getByRole('heading', { name: /agenda/i })).toBeTruthy();
    expect(screen.getByRole('heading', { name: /portabilidade/i })).toBeTruthy();
    expect(screen.getByRole('heading', { name: /auditoria/i })).toBeTruthy();
    expect(screen.queryByText(/todo:/i)).toBeNull();
  });

  it('deve enviar o opt-in quando o beneficiário informa id e convênio', async () => {
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

  it('deve exibir a falha da API quando a autorização retorna erro', async () => {
    mockedApiFetch.mockRejectedValue(new Error('Request failed: 502'));
    render(<App />);

    fillAuthorizationForm();
    fireEvent.click(screen.getByRole('button', { name: /autorizar/i }));

    const alert = await screen.findByRole('alert');
    expect(alert.textContent).toContain('502');
  });

  it('deve exibir a mensagem devolvida pela API quando a autorização falha', async () => {
    mockedApiFetch.mockRejectedValue(new Error('Central indisponível'));
    render(<App />);

    fillAuthorizationForm();
    fireEvent.click(screen.getByRole('button', { name: /autorizar/i }));

    const alert = await screen.findByRole('alert');
    expect(alert.textContent).toContain('Central indisponível');
  });

  it('deve habilitar a agenda somente depois da autorização', async () => {
    respondByPath({ '/authorizations': authorization });
    render(<App />);

    expect((screen.getByRole('button', { name: /consultar agenda/i }) as HTMLButtonElement).disabled).toBe(true);

    await authorize();

    expect((screen.getByRole('button', { name: /consultar agenda/i }) as HTMLButtonElement).disabled).toBe(false);
  });

  it('deve enviar o beneficiário autorizado quando a agenda é consultada', async () => {
    respondByPath({ '/authorizations': authorization });
    render(<App />);
    await authorize();

    respondWith(installments);
    fireEvent.click(screen.getByRole('button', { name: /consultar agenda/i }));

    await waitFor(() => {
      expect(mockedApiFetch).toHaveBeenCalledWith('/beneficiaries/BEN-123/agenda');
    });
  });

  it('deve exibir convênio, valor, data e status das parcelas consultadas', async () => {
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

  it('deve exibir estado vazio quando a agenda não tem parcelas', async () => {
    respondByPath({ '/authorizations': authorization });
    render(<App />);
    await authorize();
    respondWith([]);
    fireEvent.click(screen.getByRole('button', { name: /consultar agenda/i }));

    expect(await screen.findByText(/nenhuma parcela futura disponível/i)).toBeTruthy();
  });

  it('deve exibir a mensagem de erro da API quando a consulta da agenda falha', async () => {
    respondByPath({ '/authorizations': authorization });
    render(<App />);
    await authorize();
    mockedApiFetch.mockRejectedValueOnce(new Error('Beneficiário não autorizado'));
    fireEvent.click(screen.getByRole('button', { name: /consultar agenda/i }));

    const alert = await screen.findByRole('alert');
    expect(alert.textContent).toContain('Beneficiário não autorizado');
  });

  it('deve permitir selecionar apenas parcelas abertas e futuras', async () => {
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

  it('deve enviar conta, credor e parcelas selecionadas quando a operação é registrada', async () => {
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
          installments: ['INS-1'],
          creditorAssignee: 'Nova Credora S.A.',
          receivingAccount: { bank: '001', agency: '1234', account: '56789-0' },
        }),
      });
    });
  });

  it('deve exibir o motivo da rejeição quando a operação é rejeitada', async () => {
    const rejected: PortabilityOperation = {
      operationId: 'OPE-2',
      status: 'REJECTED',
      protocol: 'PRT-2',
      rejectionReason: 'Convênio bloqueado',
    };
    respondByPath({ '/authorizations': authorization, '/portability': rejected });
    render(<App />);

    await authorize();
    fillOperationForm();
    fireEvent.click(screen.getByRole('button', { name: /registrar portabilidade/i }));

    const alert = await screen.findByRole('alert');
    expect(alert.textContent).toContain('Convênio bloqueado');
  });

  it('deve exibir os eventos quando a auditoria é consultada', async () => {
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

  it('deve exibir estado vazio quando a auditoria não tem eventos', async () => {
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
});
