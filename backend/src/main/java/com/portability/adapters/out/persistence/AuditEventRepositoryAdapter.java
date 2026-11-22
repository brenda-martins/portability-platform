package com.portability.adapters.out.persistence;

import com.portability.domain.model.AuditEvent;
import com.portability.domain.ports.out.AuditEventRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuditEventRepositoryAdapter implements AuditEventRepository {

    private final SpringDataAuditEventRepository repository;

    public AuditEventRepositoryAdapter(SpringDataAuditEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public AuditEvent append(AuditEvent event) {
        // TODO: append-only mapping and persistence (events are never overwritten).
        throw new UnsupportedOperationException("TODO: append audit event");
    }

    @Override
    public List<AuditEvent> findByOperationId(String operationId) {
        // TODO: query and map to the domain model.
        throw new UnsupportedOperationException("TODO: find audit events");
    }
}
