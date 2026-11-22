package com.portability.domain.ports.out;

import com.portability.domain.model.OperationStatus;
import com.portability.domain.model.PortabilityOperation;

import java.util.Optional;

public interface OperationRepository {

    PortabilityOperation save(PortabilityOperation operation, String idempotencyKey);

    Optional<PortabilityOperation> findByIdempotencyKey(String idempotencyKey);

    Optional<PortabilityOperation> findById(String operationId);

    Optional<PortabilityOperation> findByProtocol(String protocol);

    PortabilityOperation updateStatus(String operationId, OperationStatus status, String rejectionReason);
}
