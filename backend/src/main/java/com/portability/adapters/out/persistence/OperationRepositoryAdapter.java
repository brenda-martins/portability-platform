package com.portability.adapters.out.persistence;

import com.portability.domain.model.OperationStatus;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.ports.out.OperationRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class OperationRepositoryAdapter implements OperationRepository {

    private final SpringDataOperationRepository repository;

    public OperationRepositoryAdapter(SpringDataOperationRepository repository) {
        this.repository = repository;
    }

    @Override
    public PortabilityOperation save(PortabilityOperation operation, String idempotencyKey) {
        OperationJpaEntity entity = new OperationJpaEntity();
        entity.setOperationId(operation.operationId());
        entity.setBeneficiaryId(operation.beneficiaryId());
        entity.setIdempotencyKey(idempotencyKey);
        entity.setInstallmentIds(new ArrayList<>(operation.installmentIds()));
        entity.setCreditorAssignee(operation.creditorAssignee());
        entity.setBank(operation.receivingAccount().bank());
        entity.setAgency(operation.receivingAccount().agency());
        entity.setAccount(operation.receivingAccount().account());
        entity.setStatus(operation.status());
        entity.setProtocol(operation.protocol());
        entity.setRejectionReason(operation.rejectionReason());
        entity.setRequestedAt(operation.requestedAt());
        return toDomain(repository.save(entity));
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

    @Override
    public Optional<PortabilityOperation> findByProtocol(String protocol) {
        return repository.findByProtocol(protocol).map(OperationRepositoryAdapter::toDomain);
    }

    @Override
    public PortabilityOperation updateStatus(String operationId, OperationStatus status, String rejectionReason) {
        OperationJpaEntity entity = repository.findById(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Operação não encontrada: " + operationId));
        entity.setStatus(status);
        entity.setRejectionReason(rejectionReason);
        return toDomain(repository.save(entity));
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
                entity.getRequestedAt());
    }
}
