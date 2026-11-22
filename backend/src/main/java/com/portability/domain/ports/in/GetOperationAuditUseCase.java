package com.portability.domain.ports.in;

import com.portability.domain.model.AuditEvent;

import java.util.List;
import java.util.Optional;

public interface GetOperationAuditUseCase {

    Optional<List<AuditEvent>> getAuditTrail(String operationId);
}
