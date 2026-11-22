package com.portability.domain.ports.in;

import com.portability.domain.model.PortabilityOperation;

import java.util.Optional;

public interface GetOperationStatusUseCase {

    Optional<PortabilityOperation> getOperationStatus(String operationId);
}
