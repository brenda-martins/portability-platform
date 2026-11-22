package com.portability.application;

import com.portability.domain.model.AuditEvent;
import com.portability.domain.model.AuditEventType;
import com.portability.domain.model.OperationId;
import com.portability.domain.ports.out.AuditEventRepository;
import com.portability.domain.ports.out.CentralUnavailableException;
import com.portability.domain.ports.out.IdGenerator;
import com.portability.domain.ports.out.MetricsPort;
import com.portability.domain.ports.out.TransactionPort;

import java.time.Clock;

/** Appends the integration-failure audit event and its metric in one place. */
public final class IntegrationFailureRecorder {

    private final AuditEventRepository auditEventRepository;
    private final TransactionPort transactions;
    private final Clock clock;
    private final IdGenerator ids;
    private final MetricsPort metrics;

    public IntegrationFailureRecorder(
            final AuditEventRepository auditEventRepository,
            final TransactionPort transactions,
            final Clock clock,
            final IdGenerator ids,
            final MetricsPort metrics
    ) {
        this.auditEventRepository = auditEventRepository;
        this.transactions = transactions;
        this.clock = clock;
        this.ids = ids;
        this.metrics = metrics;
    }

    public void record(OperationId operationId, String context, CentralUnavailableException exception) {
        transactions.inTransaction(() -> {
            auditEventRepository.append(new AuditEvent(
                    ids.next(), 
                    operationId, 
                    AuditEventType.INTEGRATION_FAILURE,
                    context + ": " + exception.getMessage(), 
                    clock.instant()
                )
            );
            return null;
        });
        metrics.increment("operation.integration-failure");
    }
}
