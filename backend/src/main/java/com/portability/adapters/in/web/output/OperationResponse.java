package com.portability.adapters.in.web.output;

import com.portability.domain.model.OperationStatus;
import com.portability.domain.model.PortabilityOperation;

import java.time.Instant;
import java.util.List;

public record OperationResponse(
        String operationId,
        String beneficiaryId,
        List<String> installmentIds,
        String creditorAssignee,
        ReceivingAccountResponse receivingAccount,
        OperationStatus status,
        String protocol,
        String rejectionReason,
        Instant requestedAt
) {
    public static OperationResponse from(PortabilityOperation operation) {
        return new OperationResponse(
                operation.operationId().value(),
                operation.beneficiaryId().value(),
                operation.installmentIds(),
                operation.creditorAssignee(),
                ReceivingAccountResponse.from(operation.receivingAccount()),
                operation.status(),
                operation.protocol() == null ? null : operation.protocol().value(),
                operation.rejectionReason(),
                operation.requestedAt());
    }
}
