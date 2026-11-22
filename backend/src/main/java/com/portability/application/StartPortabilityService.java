package com.portability.application;

import com.portability.domain.model.AuditEvent;
import com.portability.domain.model.AuditEventType;
import com.portability.domain.model.BeneficiaryNotAuthorizedException;
import com.portability.domain.model.ConcurrentOperationException;
import com.portability.domain.model.DuplicateIdempotencyKeyException;
import com.portability.domain.model.OperationId;
import com.portability.domain.model.OperationStatus;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.PortabilityOperationResult;
import com.portability.domain.model.StartPortabilityCommand;
import com.portability.domain.model.Validation;
import com.portability.domain.ports.in.StartPortabilityUseCase;
import com.portability.domain.ports.out.AuditEventRepository;
import com.portability.domain.ports.out.AuthorizationRepository;
import com.portability.domain.ports.out.CentralPortabilityPort;
import com.portability.domain.ports.out.DiscountAgendaPort;
import com.portability.domain.ports.out.IdGenerator;
import com.portability.domain.ports.out.MetricsPort;
import com.portability.domain.ports.out.OperationRepository;
import com.portability.domain.ports.out.TransactionPort;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public class StartPortabilityService implements StartPortabilityUseCase {

    private final OperationRepository operationRepository;
    private final AuthorizationRepository authorizationRepository;
    private final AuditEventRepository auditEventRepository;
    private final DiscountAgendaPort discountAgendaPort;
    private final CentralPortabilityPort centralPortabilityPort;
    private final TransactionPort transactions;
    private final Clock clock;
    private final IdGenerator ids;
    private final MetricsPort metrics;

    public StartPortabilityService(
        final OperationRepository operationRepository,
        final AuthorizationRepository authorizationRepository,
        final AuditEventRepository auditEventRepository,
        final DiscountAgendaPort discountAgendaPort,
        final CentralPortabilityPort centralPortabilityPort,
        final TransactionPort transactions,
        final Clock clock,
        final IdGenerator ids,
        final MetricsPort metrics
    ) {
        this.operationRepository = operationRepository;
        this.authorizationRepository = authorizationRepository;
        this.auditEventRepository = auditEventRepository;
        this.discountAgendaPort = discountAgendaPort;
        this.centralPortabilityPort = centralPortabilityPort;
        this.transactions = transactions;
        this.clock = clock;
        this.ids = ids;
        this.metrics = metrics;
    }

    @Override
    public PortabilityOperationResult startPortability(StartPortabilityCommand command) {
        String idempotencyKey = PortabilityOperation.idempotencyKey(command);

        try {
            return transactions.inTransaction(() -> {
                Optional<PortabilityOperation> existing = operationRepository.findByIdempotencyKey(idempotencyKey);
                if (existing.isPresent()) {
                    return resend(existing.get());
                }
                operationRepository.reserveIdempotencyKey(idempotencyKey);

                if (!authorizationRepository.existsByBeneficiaryId(command.beneficiaryId())) {
                    throw new BeneficiaryNotAuthorizedException("Beneficiary not authorized");
                }

                Validation.requireEligibleInstallments(
                        command.installmentIds(),
                        discountAgendaPort.fetchAgenda(command.beneficiaryId()),
                        LocalDate.now(clock)
                    );

                var protocol = centralPortabilityPort.registerPortability(command);
                Instant requestedAt = Instant.now(clock);

                PortabilityOperation operation = new PortabilityOperation(
                        new OperationId(ids.next()),
                        command.beneficiaryId(),
                        command.installmentIds(),
                        command.creditorAssignee(),
                        command.receivingAccount(),
                        OperationStatus.PENDING,
                        protocol,
                        null,
                        requestedAt,
                        0L
                    );

                PortabilityOperation saved = operationRepository.save(operation, idempotencyKey);
                auditEventRepository.append(new AuditEvent(
                        ids.next(),
                        saved.operationId(),
                        AuditEventType.REQUESTED,
                        "Operation requested with protocol " + protocol.value(),
                        requestedAt
                    ));
                metrics.increment("portability.created");
                return new PortabilityOperationResult(saved, true);
            });
        } catch (DuplicateIdempotencyKeyException exception) {
            return transactions.inTransaction(() -> operationRepository.findByIdempotencyKey(idempotencyKey)
                    .map(this::resend)
                    .orElseThrow(() -> new ConcurrentOperationException(
                            "Operation being recorded; please try again")));
        }
    }

    private PortabilityOperationResult resend(PortabilityOperation operation) {
        metrics.increment("portability.resend");
        return new PortabilityOperationResult(operation, false);
    }
}
