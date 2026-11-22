package com.portability.application;

import com.portability.domain.model.AuditEvent;
import com.portability.domain.model.Authorization;
import com.portability.domain.model.AuthorizationResult;
import com.portability.domain.model.BeneficiaryNotAuthorizedException;
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
import com.portability.domain.ports.out.CentralAuthorizationPort;
import com.portability.domain.ports.out.CentralPortabilityPort;
import com.portability.domain.ports.out.DiscountAgendaPort;
import com.portability.domain.ports.out.OperationRepository;
import com.portability.domain.ports.out.SignaturePort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Application layer: orchestrates the use cases using only domain ports.
 */
@Service
public class PortabilityApplicationService implements RegisterAuthorizationUseCase, GetDiscountAgendaUseCase,
        StartPortabilityUseCase, GetOperationStatusUseCase, GetOperationAuditUseCase {

    private final AuthorizationRepository authorizationRepository;
    private final OperationRepository operationRepository;
    private final AuditEventRepository auditEventRepository;
    private final DiscountAgendaPort discountAgendaPort;
    private final CentralPortabilityPort centralPortabilityPort;
    private final CentralAuthorizationPort centralAuthorizationPort;
    private final SignaturePort signaturePort;

    public PortabilityApplicationService(
            AuthorizationRepository authorizationRepository,
            OperationRepository operationRepository,
            AuditEventRepository auditEventRepository,
            DiscountAgendaPort discountAgendaPort,
            CentralPortabilityPort centralPortabilityPort,
            CentralAuthorizationPort centralAuthorizationPort,
            SignaturePort signaturePort
    ) {
        this.authorizationRepository = authorizationRepository;
        this.operationRepository = operationRepository;
        this.auditEventRepository = auditEventRepository;
        this.discountAgendaPort = discountAgendaPort;
        this.centralPortabilityPort = centralPortabilityPort;
        this.centralAuthorizationPort = centralAuthorizationPort;
        this.signaturePort = signaturePort;
    }

    @Override
    public AuthorizationResult registerAuthorization(String beneficiaryId, String agreementId) {
        requireNotBlank(beneficiaryId, "beneficiaryId");
        requireNotBlank(agreementId, "agreementId");

        Optional<Authorization> existing =
                authorizationRepository.findByBeneficiaryAndAgreement(beneficiaryId, agreementId);
        if (existing.isPresent()) {
            return new AuthorizationResult(existing.get(), false);
        }

        String authorizationId = centralAuthorizationPort.registerAuthorization(beneficiaryId, agreementId);
        Authorization authorization =
                new Authorization(authorizationId, beneficiaryId, agreementId, Instant.now());
        return new AuthorizationResult(authorizationRepository.save(authorization), true);
    }

   

    @Override
    public List<DiscountInstallment> getAgenda(String beneficiaryId) {
        requireNotBlank(beneficiaryId, "beneficiaryId");
        if (!authorizationRepository.existsByBeneficiaryId(beneficiaryId)) {
            throw new BeneficiaryNotAuthorizedException("Beneficiário não autorizado");
        }
        return discountAgendaPort.fetchAgenda(beneficiaryId);
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
