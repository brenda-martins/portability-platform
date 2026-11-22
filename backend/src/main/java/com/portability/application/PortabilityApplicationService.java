package com.portability.application;

import com.portability.domain.model.AuditEvent;
import com.portability.domain.model.Authorization;
import com.portability.domain.model.AuthorizationResult;
import com.portability.domain.model.BeneficiaryNotAuthorizedException;
import com.portability.domain.model.DiscountInstallment;
import com.portability.domain.model.InstallmentStatus;
import com.portability.domain.model.OperationStatus;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.PortabilityOperationResult;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
            throw new BeneficiaryNotAuthorizedException("Beneficiary not authorized");
        }
        return discountAgendaPort.fetchAgenda(beneficiaryId);
    }

    @Override
    public PortabilityOperationResult startPortability(
            String beneficiaryId,
            List<String> installmentIds,
            String creditorAssignee,
            ReceivingAccount receivingAccount
    ) {
        requireNotBlank(beneficiaryId, "beneficiaryId");
        requireNotBlank(creditorAssignee, "creditorAssignee");
        requireAccount(receivingAccount);
        requireSelection(installmentIds);

        String idempotencyKey = idempotencyKey(beneficiaryId, installmentIds, creditorAssignee, receivingAccount);
        Optional<PortabilityOperation> existing = operationRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return new PortabilityOperationResult(existing.get(), false);
        }

        if (!authorizationRepository.existsByBeneficiaryId(beneficiaryId)) {
            throw new BeneficiaryNotAuthorizedException("Beneficiary not authorized");
        }
        requireEligibleInstallments(installmentIds, discountAgendaPort.fetchAgenda(beneficiaryId));

        String protocol = centralPortabilityPort.registerPortability(
                beneficiaryId, installmentIds, creditorAssignee, receivingAccount);
        Instant requestedAt = Instant.now();
        PortabilityOperation operation = new PortabilityOperation(
                UUID.randomUUID().toString(),
                beneficiaryId,
                List.copyOf(installmentIds),
                creditorAssignee,
                receivingAccount,
                OperationStatus.PENDING,
                protocol,
                null,
                requestedAt);
        PortabilityOperation saved = operationRepository.save(operation, idempotencyKey);
        auditEventRepository.append(new AuditEvent(
                UUID.randomUUID().toString(),
                saved.operationId(),
                "SOLICITADA",
                "Operação solicitada com protocolo " + protocol,
                requestedAt));
        return new PortabilityOperationResult(saved, true);
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

    private static void requireAccount(ReceivingAccount account) {
        if (account == null) {
            throw new IllegalArgumentException("receivingAccount must not be null");
        }
        requireNotBlank(account.bank(), "bank");
        requireNotBlank(account.agency(), "agency");
        requireNotBlank(account.account(), "account");
    }

    private static void requireSelection(List<String> installmentIds) {
        if (installmentIds == null || installmentIds.isEmpty()) {
            throw new IllegalArgumentException("installmentIds must not be empty");
        }
        if (installmentIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("installmentIds must not contain blank values");
        }
        if (new HashSet<>(installmentIds).size() != installmentIds.size()) {
            throw new IllegalArgumentException("installmentIds must not contain duplicates");
        }
    }

    private static void requireEligibleInstallments(List<String> installmentIds,
                                                    List<DiscountInstallment> agenda) {
        Map<String, DiscountInstallment> byId = agenda.stream()
                .collect(Collectors.toMap(DiscountInstallment::installmentId, Function.identity(),
                        (first, ignored) -> first));
        LocalDate today = LocalDate.now();
        for (String installmentId : installmentIds) {
            DiscountInstallment installment = byId.get(installmentId);
            if (installment == null || installment.status() != InstallmentStatus.OPEN
                    || !installment.discountDate().isAfter(today)) {
                throw new IllegalArgumentException("Parcela inelegível: " + installmentId);
            }
        }
    }

    private static String idempotencyKey(String beneficiaryId, List<String> installmentIds,
                                         String creditorAssignee, ReceivingAccount account) {
        String canonical = String.join("|",
                beneficiaryId,
                String.join(",", installmentIds.stream().sorted().toList()),
                creditorAssignee,
                account.bank(),
                account.agency(),
                account.account());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not compute idempotency key", exception);
        }
    }

    private static void requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
