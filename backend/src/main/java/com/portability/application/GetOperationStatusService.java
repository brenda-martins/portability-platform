package com.portability.application;

import com.portability.domain.model.AuditEvent;
import com.portability.domain.model.AuditEventType;
import com.portability.domain.model.CentralOperationStatus;
import com.portability.domain.model.OperationId;
import com.portability.domain.model.OperationStatus;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.Protocol;
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
import java.util.Map;
import java.util.Optional;

/** Follows the Central status while the operation is pending, preserving backoff interruption. */
public class GetOperationStatusService implements GetOperationStatusUseCase {

    private static final int MAX_STATUS_ATTEMPTS = 3;
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(1);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(5);

    private static final Map<OperationStatus, StatusOutcome> OUTCOMES = Map.of(
            OperationStatus.CONFIRMED, 
            new StatusOutcome(AuditEventType.CONFIRMED,
           (central, protocol) -> "Operation confirmed by Central with protocol " + protocol.value()),
            OperationStatus.REJECTED, 
            new StatusOutcome(AuditEventType.REJECTED,
            (central, protocol) -> "Operation rejected by Central with protocol " + protocol.value() + ": " + central.rejectionReason())
    );

    private final OperationRepository operationRepository;
    private final AuditEventRepository auditEventRepository;
    private final CentralPortabilityPort centralPortabilityPort;
    private final BackoffPort backoffPort;
    private final TransactionPort transactions;
    private final IntegrationFailureRecorder failureRecorder;
    private final Clock clock;
    private final IdGenerator ids;
    private final MetricsPort metrics;

    public GetOperationStatusService(
        final OperationRepository operationRepository,
        final AuditEventRepository auditEventRepository,
        final CentralPortabilityPort centralPortabilityPort,
        final BackoffPort backoffPort,
        final TransactionPort transactions,
        final IntegrationFailureRecorder failureRecorder,
        final Clock clock,
        final IdGenerator ids,
        final MetricsPort metrics
    ) {
        this.operationRepository = operationRepository;
        this.auditEventRepository = auditEventRepository;
        this.centralPortabilityPort = centralPortabilityPort;
        this.backoffPort = backoffPort;
        this.transactions = transactions;
        this.failureRecorder = failureRecorder;
        this.clock = clock;
        this.ids = ids;
        this.metrics = metrics;
    }

    @Override
    public Optional<PortabilityOperation> getOperationStatus(OperationId operationId) {
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
                failureRecorder.record(operationId, "Failed to consult status at Central", exception);
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

            StatusOutcome outcome = OUTCOMES.get(central.status());
            if (outcome == null) {
                CentralUnavailableException exception =
                        new CentralUnavailableException("Unexpected response from Central");
                failureRecorder.record(operationId, "Failed to interpret status from Central", exception);
                throw exception;
            }

            PortabilityOperation transitioned = current.transitionTo(central.status(), central.rejectionReason());
            PortabilityOperation saved = transactions.inTransaction(() -> {
                PortabilityOperation persisted = operationRepository.save(transitioned);
                boolean alreadyRecorded = auditEventRepository.findByOperationId(operationId).stream()
                        .anyMatch(event -> outcome.eventType().externalValue().equals(event.eventType()));
                if (!alreadyRecorded) {
                    auditEventRepository.append(new AuditEvent(ids.next(), operationId,
                            outcome.eventType(), outcome.message().build(central, current.protocol()),
                            clock.instant()));
                }
                return persisted;
            });
            metrics.increment("operation." + outcome.eventType().name().toLowerCase());
            return Optional.of(saved);
        }
        metrics.increment("operation.still-pending");
        return Optional.of(current);
    }

    private static Duration backoffDelay(int attempt, Duration retryAfter) {
        Duration exponential = BASE_BACKOFF.multipliedBy(1L << (attempt - 1));
        Duration chosen = retryAfter == null || retryAfter.isZero() || retryAfter.isNegative()
                || retryAfter.compareTo(exponential) < 0 ? exponential : retryAfter;
        return chosen.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : chosen;
    }

    @FunctionalInterface
    private interface StatusMessage {
        String build(CentralOperationStatus central, Protocol protocol);
    }

    private record StatusOutcome(AuditEventType eventType, StatusMessage message) {
    }
}
