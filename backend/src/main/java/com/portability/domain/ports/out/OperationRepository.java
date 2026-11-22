package com.portability.domain.ports.out;

import com.portability.domain.model.PortabilityOperation;

import java.util.Optional;

public interface OperationRepository {

    PortabilityOperation save(PortabilityOperation operation, String idempotencyKey);

    /** Persists the whole aggregate state, checking the version read by the caller. */
    PortabilityOperation save(PortabilityOperation operation);

    Optional<PortabilityOperation> findByIdempotencyKey(String idempotencyKey);

    Optional<PortabilityOperation> findById(String operationId);
}
