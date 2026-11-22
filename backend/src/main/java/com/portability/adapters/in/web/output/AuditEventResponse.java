package com.portability.adapters.in.web.output;

import com.portability.domain.model.AuditEvent;

import java.time.Instant;

public record AuditEventResponse(
        String eventId,
        String operationId,
        String eventType,
        String message,
        Instant occurredAt
) {
    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.eventId(),
                event.operationId(),
                event.eventType(),
                event.message(),
                event.occurredAt());
    }
}
