package com.portability.application;

import com.portability.domain.model.AuditEvent;
import com.portability.domain.model.AuditEventType;
import com.portability.domain.model.BeneficiaryNotAuthorizedException;
import com.portability.domain.model.OperationStatus;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.PortabilityOperationResult;
import com.portability.domain.model.ReceivingAccount;
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
import java.util.List;
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
    public PortabilityOperationResult startPortability(
        String beneficiaryId, 
        List<String> installmentIds,
        String creditorAssignee, 
        ReceivingAccount receivingAccount
    ) {
        Validation.requireNotBlank(beneficiaryId, "beneficiaryId");
        Validation.requireNotBlank(creditorAssignee, "creditorAssignee");
        Validation.requireAccount(receivingAccount);
        
        List<String> selection = Validation.requireSelection(installmentIds);
        String idempotencyKey = PortabilityOperation.idempotencyKey(
                beneficiaryId, 
                selection, 
                creditorAssignee, 
                receivingAccount
            );

        return transactions.inTransaction(() -> {
            Optional<PortabilityOperation> existing = operationRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                metrics.increment("portability.resend");
                return new PortabilityOperationResult(existing.get(), false);
            }

            if (!authorizationRepository.existsByBeneficiaryId(beneficiaryId)) {
                throw new BeneficiaryNotAuthorizedException("Beneficiary not authorized");
            }

            Validation.requireEligibleInstallments(
                selection,
                discountAgendaPort.fetchAgenda(beneficiaryId), 
                LocalDate.now(clock)
            );

            String protocol = centralPortabilityPort.registerPortability(
                    beneficiaryId, 
                    selection, 
                    creditorAssignee, 
                    receivingAccount
                );
            Instant requestedAt = Instant.now(clock);

            PortabilityOperation operation = new PortabilityOperation(
                    ids.next(), 
                    beneficiaryId, 
                    selection, 
                    creditorAssignee, 
                    receivingAccount,
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
                    "Operação solicitada com protocolo " + protocol, 
                    requestedAt
                )
            );
            metrics.increment("portability.created");
            return new PortabilityOperationResult(saved, true);
        });
    }
}
