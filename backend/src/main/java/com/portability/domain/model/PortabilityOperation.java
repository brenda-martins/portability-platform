package com.portability.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

public record PortabilityOperation(
        OperationId operationId,
        BeneficiaryId beneficiaryId,
        List<String> installmentIds,
        String creditorAssignee,
        ReceivingAccount receivingAccount,
        OperationStatus status,
        Protocol protocol,
        String rejectionReason,
        Instant requestedAt,
        long version
) {
    public PortabilityOperation {
        Validation.requireNonNull(operationId, "operationId");
        Validation.requireNonNull(beneficiaryId, "beneficiaryId");
        Validation.requireNotBlank(creditorAssignee, "creditorAssignee");
        Validation.requireSelection(installmentIds);
        Validation.requireAccount(receivingAccount);
        if (status == null) {
            throw new InvalidRequestException("status", "status must not be null");
        }
        if (requestedAt == null) {
            throw new InvalidRequestException("requestedAt", "requestedAt must not be null");
        }
        if (version < 0) {
            throw new InvalidRequestException("version", "version must not be negative");
        }
        installmentIds = List.copyOf(installmentIds);
    }

    public PortabilityOperation(
        OperationId operationId, 
        BeneficiaryId beneficiaryId,
        List<String> installmentIds, 
        String creditorAssignee, 
        ReceivingAccount receivingAccount,
        OperationStatus status, 
        Protocol protocol, 
        String rejectionReason, 
        Instant requestedAt
    ) {
        this(
            operationId, 
            beneficiaryId, 
            installmentIds, 
            creditorAssignee, 
            receivingAccount,
            status, 
            protocol, 
            rejectionReason, 
            requestedAt, 
            0L
        );
    }

    /** Only a pending operation may reach a terminal status, and only once. */
    public PortabilityOperation transitionTo(OperationStatus newStatus, String reason) {
        if (status != OperationStatus.PENDING
                || (newStatus != OperationStatus.CONFIRMED && newStatus != OperationStatus.REJECTED)) {
            throw new InvalidRequestException("status",
                    "Invalid status transition: " + status + " -> " + newStatus);
        }
        String rejectionReason = newStatus == OperationStatus.REJECTED
                ? Validation.requireNotBlank(reason, "rejectionReason") : null;
        return new PortabilityOperation(operationId, beneficiaryId, installmentIds, creditorAssignee,
                receivingAccount, newStatus, protocol, rejectionReason, requestedAt, version);
    }

    /** The receiving account can be corrected only after confirmation. */
    public PortabilityOperation withReceivingAccount(ReceivingAccount account) {
        if (status != OperationStatus.CONFIRMED) {
            throw new OperationNotConfirmedException("Only confirmed operations may have the receiving account changed");
        }
        ReceivingAccount validated = Validation.requireAccount(account);
        if (receivingAccount.equals(validated)) {
            return this;
        }
        return new PortabilityOperation(operationId, beneficiaryId, installmentIds, creditorAssignee,
                validated, status, protocol, rejectionReason, requestedAt, version);
    }

    public boolean isPending() {
        return status == OperationStatus.PENDING && protocol != null;
    }

    /** Hash that recognises a resend of the same portability request. */
    public static String idempotencyKey(StartPortabilityCommand command) {
        String canonical = String.join("|",
                command.beneficiaryId().value(),
                String.join(",", command.installmentIds().stream().sorted().toList()),
                command.creditorAssignee(),
                command.receivingAccount().bank(),
                command.receivingAccount().agency(),
                command.receivingAccount().account());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not compute idempotency key", exception);
        }
    }
}
