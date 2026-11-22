package com.portability.application;

import com.portability.domain.model.AuditEvent;
import com.portability.domain.model.Authorization;
import com.portability.domain.model.AuthorizationResult;
import com.portability.domain.model.BeneficiaryNotAuthorizedException;
import com.portability.domain.model.CentralOperationStatus;
import com.portability.domain.model.DiscountInstallment;
import com.portability.domain.model.InstallmentStatus;
import com.portability.domain.model.OperationNotConfirmedException;
import com.portability.domain.model.OperationStatus;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.PortabilityOperationResult;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.ports.in.GetDiscountAgendaUseCase;
import com.portability.domain.ports.in.GetOperationAuditUseCase;
import com.portability.domain.ports.in.GetOperationStatusUseCase;
import com.portability.domain.ports.in.RegisterAuthorizationUseCase;
import com.portability.domain.ports.in.StartPortabilityUseCase;
import com.portability.domain.ports.in.UpdateReceivingAccountUseCase;
import com.portability.domain.ports.out.AuditEventRepository;
import com.portability.domain.ports.out.AuthorizationRepository;
import com.portability.domain.ports.out.BackoffPort;
import com.portability.domain.ports.out.CentralAuthorizationPort;
import com.portability.domain.ports.out.CentralPortabilityPort;
import com.portability.domain.ports.out.CentralUnavailableException;
import com.portability.domain.ports.out.DiscountAgendaPort;
import com.portability.domain.ports.out.OperationRepository;
import com.portability.domain.ports.out.SignaturePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
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
public class PortabilityApplicationService implements
    RegisterAuthorizationUseCase,
    GetDiscountAgendaUseCase,
    StartPortabilityUseCase,
    GetOperationStatusUseCase,
    UpdateReceivingAccountUseCase,
    GetOperationAuditUseCase {

    private static final int MAX_STATUS_ATTEMPTS = 3;
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(1);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(5);

    private final AuthorizationRepository authorizationRepository;
    private final OperationRepository operationRepository;
    private final AuditEventRepository auditEventRepository;
    private final DiscountAgendaPort discountAgendaPort;
    private final CentralPortabilityPort centralPortabilityPort;
    private final CentralAuthorizationPort centralAuthorizationPort;
    private final SignaturePort signaturePort;
    private final BackoffPort backoffPort;

    public PortabilityApplicationService(
            AuthorizationRepository authorizationRepository,
            OperationRepository operationRepository,
            AuditEventRepository auditEventRepository,
            DiscountAgendaPort discountAgendaPort,
            CentralPortabilityPort centralPortabilityPort,
            CentralAuthorizationPort centralAuthorizationPort,
            SignaturePort signaturePort,
            BackoffPort backoffPort) {
        this.authorizationRepository = authorizationRepository;
        this.operationRepository = operationRepository;
        this.auditEventRepository = auditEventRepository;
        this.discountAgendaPort = discountAgendaPort;
        this.centralPortabilityPort = centralPortabilityPort;
        this.centralAuthorizationPort = centralAuthorizationPort;
        this.signaturePort = signaturePort;
        this.backoffPort = backoffPort;
    }

    @Override
    @Transactional
    public AuthorizationResult registerAuthorization(String beneficiaryId, String agreementId) {
        requireNotBlank(beneficiaryId, "beneficiaryId");
        requireNotBlank(agreementId, "agreementId");

        Optional<Authorization> existing = authorizationRepository.findByBeneficiaryAndAgreement(beneficiaryId,
                agreementId);
        if (existing.isPresent()) {
            return new AuthorizationResult(existing.get(), false);
        }

        String authorizationId = centralAuthorizationPort.registerAuthorization(beneficiaryId, agreementId);
        Authorization authorization = new Authorization(authorizationId, beneficiaryId, agreementId, Instant.now());
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
    @Transactional
    public PortabilityOperationResult startPortability(
            String beneficiaryId,
            List<String> installmentIds,
            String creditorAssignee,
            ReceivingAccount receivingAccount) {
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
    @Transactional
    public Optional<PortabilityOperation> getOperationStatus(String operationId) {
        requireNotBlank(operationId, "operationId");
        return operationRepository.findById(operationId).map(this::followStatus);
    }

    private PortabilityOperation followStatus(PortabilityOperation operation) {
        if (operation.status() != OperationStatus.PENDING
                || operation.protocol() == null || operation.protocol().isBlank()) {
            return operation;
        }

        PortabilityOperation current = operation;
        for (int attempt = 1; attempt <= MAX_STATUS_ATTEMPTS && current.status() == OperationStatus.PENDING;
                attempt++) {
            CentralOperationStatus central;
            try {
                central = centralPortabilityPort.consultStatus(current.protocol());
            } catch (CentralUnavailableException exception) {
                appendIntegrationFailure(current.operationId(), "Falha ao consultar status na Central", exception);
                throw exception;
            }
            if (central.status() == OperationStatus.PENDING) {
                if (attempt < MAX_STATUS_ATTEMPTS) {
                    backoffPort.await(backoffDelay(attempt, central.retryAfter()));
                }
                continue;
            }

            PortabilityOperation updated = operationRepository.updateStatus(
                    current.operationId(), central.status(), central.rejectionReason());
            appendTransitionEvent(updated, central);
            current = updated;
        }
        return current;
    }

    private void appendTransitionEvent(PortabilityOperation operation, CentralOperationStatus central) {
        String type = eventType(central.status());
        boolean alreadyRecorded = auditEventRepository.findByOperationId(operation.operationId()).stream()
                .anyMatch(event -> type.equals(event.eventType()));
        if (alreadyRecorded) {
            return;
        }
        auditEventRepository.append(new AuditEvent(
                UUID.randomUUID().toString(),
                operation.operationId(),
                type,
                statusMessage(central, operation.protocol()),
                Instant.now()));
    }

    private void appendIntegrationFailure(String operationId, String context,
            CentralUnavailableException exception) {
        auditEventRepository.append(new AuditEvent(
                UUID.randomUUID().toString(),
                operationId,
                "FALHA_INTEGRACAO",
                context + ": " + exception.getMessage(),
                Instant.now()));
    }

    private static Duration backoffDelay(int attempt, Duration retryAfter) {
        Duration exponential = BASE_BACKOFF.multipliedBy(1L << (attempt - 1));
        Duration chosen = retryAfter == null || retryAfter.isZero() || retryAfter.isNegative()
                || retryAfter.compareTo(exponential) < 0 ? exponential : retryAfter;
        return chosen.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : chosen;
    }

    private static String eventType(OperationStatus status) {
        return switch (status) {
            case CONFIRMED -> "CONFIRMADA";
            case REJECTED -> "REJEITADA";
            case PENDING -> "PENDENTE";
        };
    }

    private static String statusMessage(CentralOperationStatus central, String protocol) {
        if (central.status() == OperationStatus.REJECTED) {
            return "Operação rejeitada pela Central: " + central.rejectionReason();
        }
        return "Operação confirmada pela Central com protocolo " + protocol;
    }

    @Override
    @Transactional
    public Optional<PortabilityOperation> updateReceivingAccount(
            String operationId,
            ReceivingAccount receivingAccount
        ) {
        requireNotBlank(operationId, "operationId");
        requireAccount(receivingAccount);

        Optional<PortabilityOperation> existing = operationRepository.findById(operationId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        PortabilityOperation operation = existing.get();
        if (operation.status() != OperationStatus.CONFIRMED) {
            throw new OperationNotConfirmedException(
                    "Somente operações confirmadas podem ter a conta alterada");
        }
        if (operation.receivingAccount().equals(receivingAccount)) {
            return existing;
        }

        try {
            centralPortabilityPort.updateReceivingAccount(operation.protocol(), receivingAccount);
        } catch (CentralUnavailableException exception) {
            appendIntegrationFailure(operationId, "Falha ao atualizar conta na Central", exception);
            throw exception;
        }
        PortabilityOperation updated = operationRepository.updateReceivingAccount(operationId, receivingAccount);
        auditEventRepository.append(new AuditEvent(
                UUID.randomUUID().toString(),
                updated.operationId(),
                "CONTA_ATUALIZADA",
                "Conta de recebimento atualizada para o protocolo " + operation.protocol() + ": "
                        + receivingAccount.bank() + "/" + receivingAccount.agency() + "/"
                        + receivingAccount.account(),
                Instant.now()));
        return Optional.of(updated);
    }

    @Override
    public Optional<List<AuditEvent>> getAuditTrail(String operationId) {
        requireNotBlank(operationId, "operationId");
        if (operationRepository.findById(operationId).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(auditEventRepository.findByOperationId(operationId));
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
