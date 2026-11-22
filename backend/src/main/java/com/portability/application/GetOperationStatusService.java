package com.portability.application;

import com.portability.domain.model.AuditEvent;
import com.portability.domain.model.AuditEventType;
import com.portability.domain.model.CentralOperationStatus;
import com.portability.domain.model.OperationStatus;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.Validation;
import com.portability.domain.ports.in.GetOperationStatusUseCase;
import com.portability.domain.ports.out.AuditEventRepository;
import com.portability.domain.ports.out.BackoffPort;
import com.portability.domain.ports.out.CentralPortabilityPort;
import com.portability.domain.ports.out.CentralUnavailableException;
import com.portability.domain.ports.out.IdGenerator;
import com.portability.domain.ports.out.MetricsPort;
import com.portability.domain.ports.out.OperationRepository;
import com.portability.domain.ports.out.TransactionPort;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/** Follows the Central status while the operation is pending, preserving backoff interruption. */
public class GetOperationStatusService implements GetOperationStatusUseCase {

    private static final int MAX_STATUS_ATTEMPTS = 3;
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(1);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(5);

    private final OperationRepository operationRepository;
    private final AuditEventRepository auditEventRepository;
    private final CentralPortabilityPort centralPortabilityPort;
    private final BackoffPort backoffPort;
    private final TransactionPort transactions;
    private final Clock clock;
    private final IdGenerator ids;
    private final MetricsPort metrics;

    public GetOperationStatusService(
        final OperationRepository operationRepository,
        final AuditEventRepository auditEventRepository, 
        final CentralPortabilityPort centralPortabilityPort,
        final BackoffPort backoffPort, 
        final TransactionPort transactions, 
        final Clock clock,
        final IdGenerator ids, 
        final MetricsPort metrics
    ) {
        this.operationRepository = operationRepository;
        this.auditEventRepository = auditEventRepository;
        this.centralPortabilityPort = centralPortabilityPort;
        this.backoffPort = backoffPort;
        this.transactions = transactions;
        this.clock = clock;
        this.ids = ids;
        this.metrics = metrics;
    }

    @Override
    public Optional<PortabilityOperation> getOperationStatus(String operationId) {
        Validation.requireNotBlank(operationId, "operationId");
        
        Optional<PortabilityOperation> found = operationRepository.findById(operationId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        PortabilityOperation current = found.get();
        if (!current.isPending()) {
            return Optional.of(current);
        }

        for (int attempt = 1; attempt <= MAX_STATUS_ATTEMPTS; attempt++) {
            CentralOperationStatus central;
            try {
                central = centralPortabilityPort.consultStatus(current.protocol());
            } catch (CentralUnavailableException exception) {
                appendFailure(current, "Falha ao consultar status na Central", exception);
                throw exception;
            }

            if (central.status() == OperationStatus.PENDING) {
                if (attempt == MAX_STATUS_ATTEMPTS) {
                    break;
                }
                try {
                    backoffPort.await(backoffDelay(attempt, central.retryAfter()));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return Optional.of(current);
                }
                continue;
            }

            AuditEventType eventType = central.status() == OperationStatus.REJECTED
                    ? AuditEventType.REJECTED : AuditEventType.CONFIRMED;
           
            PortabilityOperation transitioned = current.transitionTo(central.status(), central.rejectionReason());
           
            PortabilityOperation saved = transactions.inTransaction(() -> {
                PortabilityOperation persisted = operationRepository.save(transitioned);
                boolean alreadyRecorded = auditEventRepository.findByOperationId(operationId).stream()
                        .anyMatch(event -> eventType.externalValue().equals(event.eventType()));
                if (!alreadyRecorded) {
                    auditEventRepository.append(new AuditEvent(ids.next(), operationId, eventType,
                            statusMessage(central, current.protocol()), clock.instant()));
                }
                return persisted;
            });
            metrics.increment("operation." + eventType.name().toLowerCase());
            return Optional.of(saved);
        }
        metrics.increment("operation.still-pending");
        return Optional.of(current);
    }

    private void appendFailure(
        PortabilityOperation operation, 
        String context,
        CentralUnavailableException exception
    ) {
        transactions.inTransaction(() -> {
            auditEventRepository.append(new AuditEvent(ids.next(), operation.operationId(),
                    AuditEventType.INTEGRATION_FAILURE, context + ": " + exception.getMessage(), clock.instant()));
            return null;
        });
        metrics.increment("operation.integration-failure");
    }

    private static Duration backoffDelay(int attempt, Duration retryAfter) {
        Duration exponential = BASE_BACKOFF.multipliedBy(1L << (attempt - 1));
        Duration chosen = retryAfter == null || retryAfter.isZero() || retryAfter.isNegative()
                || retryAfter.compareTo(exponential) < 0 ? exponential : retryAfter;
        return chosen.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : chosen;
    }

    private static String statusMessage(CentralOperationStatus central, String protocol) {
        if (central.status() == OperationStatus.REJECTED) {
            return "Operação rejeitada pela Central com protocolo " + protocol
                    + ": " + central.rejectionReason();
        }
        return "Operação confirmada pela Central com protocolo " + protocol;
    }
}
