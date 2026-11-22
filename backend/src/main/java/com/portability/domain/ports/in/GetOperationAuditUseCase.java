package com.portability.domain.ports.in;

import com.portability.domain.model.AuditEvent;

import java.util.List;

public interface GetOperationAuditUseCase {

    List<AuditEvent> getAuditTrail(String operationId);
}
