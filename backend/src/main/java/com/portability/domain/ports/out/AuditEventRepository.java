package com.portability.domain.ports.out;

import com.portability.domain.model.AuditEvent;
import com.portability.domain.model.OperationId;

import java.util.List;

public interface AuditEventRepository {

    AuditEvent append(AuditEvent event);

    List<AuditEvent> findByOperationId(OperationId operationId);
}
