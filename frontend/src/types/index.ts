/** DTOs mirroring the contract suggested in the challenge PDF (names in English). */

export interface AuthorizeRequest {
  beneficiaryId: string;
  agreementId: string;
}

export interface Authorization {
  authorizationId: string;
  beneficiaryId: string;
  agreementId: string;
  createdAt: string;
}

export interface DiscountInstallment {
  installmentId: string;
  agreementId: string;
  amount: number;
  discountDate: string;
  status: 'OPEN' | 'PORTED' | 'CANCELLED';
}

export interface ReceivingAccount {
  bank: string;
  agency: string;
  account: string;
}

export interface PortabilityRequest {
  beneficiaryId: string;
  installmentIds: string[];
  creditorAssignee: string;
  receivingAccount: ReceivingAccount;
}

export interface PortabilityOperation {
  operationId: string;
  status: 'PENDING' | 'CONFIRMED' | 'REJECTED';
  protocol: string | null;
  rejectionReason: string | null;
  receivingAccount: ReceivingAccount;
}

export interface AuditEvent {
  eventId: string;
  operationId: string;
  eventType: string;
  message: string;
  occurredAt: string;
}
