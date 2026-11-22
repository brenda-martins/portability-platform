package com.portability.application.usecase;

import com.portability.application.GetOperationAuditService;
import com.portability.domain.model.AuditEvent;
import com.portability.domain.model.AuditEventType;
import com.portability.domain.model.BeneficiaryId;
import com.portability.domain.model.InvalidRequestException;
import com.portability.domain.model.OperationId;
import com.portability.domain.model.OperationStatus;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.Protocol;
import com.portability.domain.model.ReceivingAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GetOperationAuditServiceTest {

    private static final OperationId OPERATION_ID = new OperationId("OPE-1");

    private TestDoubles.State state;
    private TestDoubles.Operations operations;
    private TestDoubles.AuditEvents auditEvents;
    private TestDoubles.Metrics metrics;
    private GetOperationAuditService service;

    @BeforeEach
    void setUp() {
        state = new TestDoubles.State();
        operations = new TestDoubles.Operations(state);
        auditEvents = new TestDoubles.AuditEvents(state);
        metrics = new TestDoubles.Metrics();
        service = new GetOperationAuditService(operations, auditEvents, metrics);
    }

    @Test
    void mustReturnAuditEventsWhenOperationExists() {
        operations.seed(operation(), "key");
        Instant occurredAt = Instant.parse("2026-11-22T12:34:56Z");
        auditEvents.append(new AuditEvent("EVT-1", OPERATION_ID, AuditEventType.REQUESTED,
                "Operation requested", occurredAt));
        auditEvents.append(new AuditEvent("EVT-2", OPERATION_ID, AuditEventType.ACCOUNT_UPDATED,
                "Account updated", occurredAt.plusSeconds(60)));
        auditEvents.append(new AuditEvent("EVT-OTHER", new OperationId("OPE-OTHER"), AuditEventType.REQUESTED,
                "Another operation", occurredAt));

        List<AuditEvent> trail = service.getAuditTrail(OPERATION_ID).orElseThrow();
        assertEquals(2, trail.size());
        assertTrue(trail.stream().allMatch(event -> event.operationId().equals(OPERATION_ID)));
        assertTrue(metrics.names.contains("audit.consulted"));
    }

    @Test
    void mustReturnEmptyWhenOperationDoesNotExist() {
        assertTrue(service.getAuditTrail(new OperationId("missing")).isEmpty());
        assertTrue(state.auditEvents.isEmpty());
        assertTrue(metrics.names.isEmpty());
    }

    @Test
    void mustRejectBlankOperationIdBeforeConsultingPorts() {
        assertThrows(InvalidRequestException.class, () -> new OperationId(" "));
        assertTrue(state.auditEvents.isEmpty());
    }

    private static PortabilityOperation operation() {
        return new PortabilityOperation(OPERATION_ID, new BeneficiaryId("BEN-123"), List.of("INS-1"),
                "Nova Credora S.A.", new ReceivingAccount("001", "1234", "56789-0"),
                OperationStatus.CONFIRMED, new Protocol("PRT-1"), null,
                Instant.parse("2026-11-22T12:34:56Z"));
    }
}
