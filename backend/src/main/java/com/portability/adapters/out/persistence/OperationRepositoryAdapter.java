package com.portability.adapters.out.persistence;

import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.ports.out.OperationRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class OperationRepositoryAdapter implements OperationRepository {

    private final SpringDataOperationRepository repository;

    public OperationRepositoryAdapter(SpringDataOperationRepository repository) {
        this.repository = repository;
    }

    @Override
    public PortabilityOperation save(PortabilityOperation operation) {
        // TODO: map to/from OperationJpaEntity and persist.
        throw new UnsupportedOperationException("TODO: save operation");
    }

    @Override
    public Optional<PortabilityOperation> findById(String operationId) {
        // TODO: query and map to the domain model.
        throw new UnsupportedOperationException("TODO: find operation by id");
    }

    @Override
    public Optional<PortabilityOperation> findByProtocol(String protocol) {
        // TODO: query and map to the domain model (used for idempotency).
        throw new UnsupportedOperationException("TODO: find operation by protocol");
    }
}
