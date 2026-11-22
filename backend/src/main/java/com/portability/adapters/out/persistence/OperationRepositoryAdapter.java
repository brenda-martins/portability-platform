package com.portability.adapters.out.persistence;

import com.portability.adapters.out.persistence.entity.IdempotencyReservationJpaEntity;
import com.portability.adapters.out.persistence.entity.OperationJpaEntity;
import com.portability.domain.model.ConcurrentOperationException;
import com.portability.domain.model.DuplicateIdempotencyKeyException;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.ports.out.OperationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class OperationRepositoryAdapter implements OperationRepository {

    private final SpringDataOperationRepository repository;
    private final SpringDataIdempotencyReservationRepository reservations;

    public OperationRepositoryAdapter(
        final SpringDataOperationRepository repository,
        final SpringDataIdempotencyReservationRepository reservations
    ) {
        this.repository = repository;
        this.reservations = reservations;
    }

    @Override
    public void reserveIdempotencyKey(String idempotencyKey) {
        try {
            reservations.saveAndFlush(new IdempotencyReservationJpaEntity(idempotencyKey));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateIdempotencyKeyException(
                    "Idempotency key already reserved", exception);
        }
    }

    @Override
    public PortabilityOperation save(PortabilityOperation operation, String idempotencyKey) {
        OperationJpaEntity entity = new OperationJpaEntity();
        applyState(entity, operation);
        entity.setIdempotencyKey(idempotencyKey);
        return persist(entity);
    }

    @Override
    public PortabilityOperation save(PortabilityOperation operation) {
        OperationJpaEntity entity = repository.findById(operation.operationId())
                .orElseThrow(() -> new ConcurrentOperationException(
                        "Operação não encontrada: " + operation.operationId()));
        if (entity.getVersion() != operation.version()) {
            throw new ConcurrentOperationException(
                    "Operação alterada por outra requisição; tente novamente");
        }
        applyState(entity, operation);
        return persist(entity);
    }

    @Override
    public Optional<PortabilityOperation> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey)
                .map(OperationRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<PortabilityOperation> findById(String operationId) {
        return repository.findById(operationId).map(OperationRepositoryAdapter::toDomain);
    }

    private PortabilityOperation persist(OperationJpaEntity entity) {
        try {
            return toDomain(repository.saveAndFlush(entity));
        } catch (OptimisticLockingFailureException exception) {
            throw new ConcurrentOperationException(
                    "Operação alterada por outra requisição; tente novamente", exception);
        }
    }

    private static void applyState(OperationJpaEntity entity, PortabilityOperation operation) {
        entity.setOperationId(operation.operationId());
        entity.setBeneficiaryId(operation.beneficiaryId());
        entity.setInstallmentIds(new ArrayList<>(operation.installmentIds()));
        entity.setCreditorAssignee(operation.creditorAssignee());
        entity.setBank(operation.receivingAccount().bank());
        entity.setAgency(operation.receivingAccount().agency());
        entity.setAccount(operation.receivingAccount().account());
        entity.setStatus(operation.status());
        entity.setProtocol(operation.protocol());
        entity.setRejectionReason(operation.rejectionReason());
        entity.setRequestedAt(operation.requestedAt());
    }

    private static PortabilityOperation toDomain(OperationJpaEntity entity) {
        return new PortabilityOperation(
                entity.getOperationId(),
                entity.getBeneficiaryId(),
                List.copyOf(entity.getInstallmentIds()),
                entity.getCreditorAssignee(),
                new ReceivingAccount(entity.getBank(), entity.getAgency(), entity.getAccount()),
                entity.getStatus(),
                entity.getProtocol(),
                entity.getRejectionReason(),
                entity.getRequestedAt(),
                entity.getVersion());
    }
}
