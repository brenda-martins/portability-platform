package com.portability.domain.model;

import java.time.Instant;
import java.util.List;

/** A portability request and its current state. */
public record PortabilityOperation(
        String operationId,
        String beneficiaryId,
        List<String> installmentIds,
        String creditorAssignee,
        ReceivingAccount receivingAccount,
        OperationStatus status,
        String protocol,
        String rejectionReason,
        Instant requestedAt
) {
}
