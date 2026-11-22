package com.portability.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "audit_events")
public class AuditEventJpaEntity {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "operation_id", nullable = false)
    private String operationId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "message")
    private String message;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
