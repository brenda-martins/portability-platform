package com.portability.application;

import com.portability.domain.model.AuditEvent;
import com.portability.domain.model.Validation;
import com.portability.domain.ports.in.GetOperationAuditUseCase;
import com.portability.domain.ports.out.AuditEventRepository;
import com.portability.domain.ports.out.MetricsPort;
import com.portability.domain.ports.out.OperationRepository;

import java.util.List;
import java.util.Optional;

public class GetOperationAuditService implements GetOperationAuditUseCase {

    private final OperationRepository operationRepository;
    private final AuditEventRepository auditEventRepository;
    private final MetricsPort metrics;

    public GetOperationAuditService(
        final OperationRepository operationRepository,
        final AuditEventRepository auditEventRepository, 
        final MetricsPort metrics
    ) {
        this.operationRepository = operationRepository;
        this.auditEventRepository = auditEventRepository;
        this.metrics = metrics;
    }

    @Override
    public Optional<List<AuditEvent>> getAuditTrail(String operationId) {
        Validation.requireNotBlank(operationId, "operationId");
        if (operationRepository.findById(operationId).isEmpty()) {
            return Optional.empty();
        }
        List<AuditEvent> events = auditEventRepository.findByOperationId(operationId);
        metrics.increment("audit.consulted");
        return Optional.of(events);
    }
}
