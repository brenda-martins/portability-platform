package com.portability.domain.model;

import java.time.Instant;

/** Immutable record of a relevant state change of an operation. */
public record AuditEvent(
        String eventId,
        String operationId,
        String eventType,
        String message,
        Instant occurredAt
) {
}
