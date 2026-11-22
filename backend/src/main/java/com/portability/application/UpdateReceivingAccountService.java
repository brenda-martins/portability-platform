package com.portability.application;

import com.portability.domain.model.AuditEvent;
import com.portability.domain.model.AuditEventType;
import com.portability.domain.model.OperationId;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.model.Validation;
import com.portability.domain.ports.in.UpdateReceivingAccountUseCase;
import com.portability.domain.ports.out.AuditEventRepository;
import com.portability.domain.ports.out.CentralPortabilityPort;
import com.portability.domain.ports.out.CentralUnavailableException;
import com.portability.domain.ports.out.IdGenerator;
import com.portability.domain.ports.out.MetricsPort;
import com.portability.domain.ports.out.OperationRepository;
import com.portability.domain.ports.out.TransactionPort;

import java.time.Clock;
import java.util.Optional;

public class UpdateReceivingAccountService implements UpdateReceivingAccountUseCase {

    private final OperationRepository operationRepository;
    private final AuditEventRepository auditEventRepository;
    private final CentralPortabilityPort centralPortabilityPort;
    private final TransactionPort transactions;
    private final IntegrationFailureRecorder failureRecorder;
    private final Clock clock;
    private final IdGenerator ids;
    private final MetricsPort metrics;

    public UpdateReceivingAccountService(
        final OperationRepository operationRepository,
        final AuditEventRepository auditEventRepository,
        final CentralPortabilityPort centralPortabilityPort,
        final TransactionPort transactions,
        final IntegrationFailureRecorder failureRecorder,
        final Clock clock,
        final IdGenerator ids,
        final MetricsPort metrics
    ) {
        this.operationRepository = operationRepository;
        this.auditEventRepository = auditEventRepository;
        this.centralPortabilityPort = centralPortabilityPort;
        this.transactions = transactions;
        this.failureRecorder = failureRecorder;
        this.clock = clock;
        this.ids = ids;
        this.metrics = metrics;
    }

    @Override
    public Optional<PortabilityOperation> updateReceivingAccount(
        OperationId operationId,
        ReceivingAccount receivingAccount
    ) {
        Validation.requireAccount(receivingAccount);

        Optional<PortabilityOperation> found = operationRepository.findById(operationId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        PortabilityOperation operation = found.get();
        PortabilityOperation changed = operation.withReceivingAccount(receivingAccount);
        if (changed == operation) {
            return found;
        }

        try {
            centralPortabilityPort.updateReceivingAccount(operation.protocol(), receivingAccount);
        } catch (CentralUnavailableException exception) {
            failureRecorder.record(operationId, "Failed to update receiving account at Central", exception);
            throw exception;
        }

        PortabilityOperation saved = transactions.inTransaction(() -> {
            PortabilityOperation persisted = operationRepository.save(changed);
            auditEventRepository.append(new AuditEvent(ids.next(), operationId,
                    AuditEventType.ACCOUNT_UPDATED,
                    "Receiving account updated for protocol " + operation.protocol().value() + ": "
                            + receivingAccount.bank() + "/" + receivingAccount.agency() + "/"
                            + receivingAccount.account(),
                    clock.instant()));
            return persisted;
        });
        metrics.increment("operation.account-updated");
        return Optional.of(saved);
    }
}
