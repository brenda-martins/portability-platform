package com.portability.domain.model;

import java.time.Instant;

public record AuditEvent(
        String eventId,
        String operationId,
        String eventType,
        String message,
        Instant occurredAt
) {
    public AuditEvent {
        Validation.requireNotBlank(eventId, "eventId");
        Validation.requireNotBlank(operationId, "operationId");
        Validation.requireNotBlank(eventType, "eventType");
        if (occurredAt == null) {
            throw new InvalidRequestException("occurredAt", "occurredAt must not be null");
        }
    }

    public AuditEvent(
        String eventId, 
        String operationId, 
        AuditEventType type,
        String message, 
        Instant occurredAt
    ) {
        this(eventId, operationId, type.externalValue(), message, occurredAt);
    }
}
