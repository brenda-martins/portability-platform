package com.portability.adapters.out.persistence;

import com.portability.adapters.out.persistence.entity.AuditEventJpaEntity;
import com.portability.domain.model.AuditEvent;
import com.portability.domain.model.OperationId;
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
        AuditEventJpaEntity entity = new AuditEventJpaEntity();
        entity.setEventId(event.eventId());
        entity.setOperationId(event.operationId().value());
        entity.setEventType(event.eventType());
        entity.setMessage(event.message());
        entity.setOccurredAt(event.occurredAt());
        return toDomain(repository.save(entity));
    }

    @Override
    public List<AuditEvent> findByOperationId(OperationId operationId) {
        return repository.findByOperationIdOrderByOccurredAtAsc(operationId.value()).stream()
                .map(AuditEventRepositoryAdapter::toDomain)
                .toList();
    }

    private static AuditEvent toDomain(AuditEventJpaEntity entity) {
        return new AuditEvent(
                entity.getEventId(),
                new OperationId(entity.getOperationId()),
                entity.getEventType(),
                entity.getMessage(),
                entity.getOccurredAt());
    }
}
