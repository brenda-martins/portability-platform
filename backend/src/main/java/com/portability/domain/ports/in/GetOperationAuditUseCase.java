package com.portability.domain.ports.in;

import com.portability.domain.model.AuditEvent;
import com.portability.domain.model.OperationId;

import java.util.List;
import java.util.Optional;

public interface GetOperationAuditUseCase {

    Optional<List<AuditEvent>> getAuditTrail(OperationId operationId);
}
