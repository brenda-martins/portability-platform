package com.portability.application;

import com.portability.domain.model.AuditEvent;
import com.portability.domain.model.Authorization;
import com.portability.domain.model.DiscountInstallment;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.ports.in.GetDiscountAgendaUseCase;
import com.portability.domain.ports.in.GetOperationAuditUseCase;
import com.portability.domain.ports.in.GetOperationStatusUseCase;
import com.portability.domain.ports.in.RegisterAuthorizationUseCase;
import com.portability.domain.ports.in.StartPortabilityUseCase;
import com.portability.domain.ports.out.AuditEventRepository;
import com.portability.domain.ports.out.AuthorizationRepository;
import com.portability.domain.ports.out.CentralPortabilityPort;
import com.portability.domain.ports.out.DiscountAgendaPort;
import com.portability.domain.ports.out.OperationRepository;
import com.portability.domain.ports.out.SignaturePort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Application layer: orchestrates the use cases using only domain ports.
 * Methods are intentionally left as stubs in this initial scaffold.
 */
@Service
public class PortabilityApplicationService implements RegisterAuthorizationUseCase, GetDiscountAgendaUseCase,
        StartPortabilityUseCase, GetOperationStatusUseCase, GetOperationAuditUseCase {

    private final AuthorizationRepository authorizationRepository;
    private final OperationRepository operationRepository;
    private final AuditEventRepository auditEventRepository;
    private final DiscountAgendaPort discountAgendaPort;
    private final CentralPortabilityPort centralPortabilityPort;
    private final SignaturePort signaturePort;

    public PortabilityApplicationService(
            AuthorizationRepository authorizationRepository,
            OperationRepository operationRepository,
            AuditEventRepository auditEventRepository,
            DiscountAgendaPort discountAgendaPort,
            CentralPortabilityPort centralPortabilityPort,
            SignaturePort signaturePort
    ) {
        this.authorizationRepository = authorizationRepository;
        this.operationRepository = operationRepository;
        this.auditEventRepository = auditEventRepository;
        this.discountAgendaPort = discountAgendaPort;
        this.centralPortabilityPort = centralPortabilityPort;
        this.signaturePort = signaturePort;
    }

    @Override
    public Authorization registerAuthorization(String beneficiaryId, String agreementId) {
        // TODO: validate opt-in, persist the authorization and register it in the Central.
        throw new UnsupportedOperationException("TODO: registerAuthorization");
    }

    @Override
    public List<DiscountInstallment> getAgenda(String beneficiaryId) {
        // TODO: check authorization before fetching the agenda from the Central.
        throw new UnsupportedOperationException("TODO: getAgenda");
    }

    @Override
    public PortabilityOperation startPortability(
            String beneficiaryId,
            List<String> installmentIds,
            String creditorAssignee,
            ReceivingAccount receivingAccount
    ) {
        // TODO: idempotency key, eligibility check, HMAC signature, protocol request and audit event.
        throw new UnsupportedOperationException("TODO: startPortability");
    }

    @Override
    public Optional<PortabilityOperation> getOperationStatus(String operationId) {
        // TODO: read local state and poll the Central when the operation is still pending.
        throw new UnsupportedOperationException("TODO: getOperationStatus");
    }

    @Override
    public List<AuditEvent> getAuditTrail(String operationId) {
        // TODO: return the immutable event trail ordered by occurrence time.
        throw new UnsupportedOperationException("TODO: getAuditTrail");
    }
}
