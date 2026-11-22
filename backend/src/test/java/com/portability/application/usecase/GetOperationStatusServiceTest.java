package com.portability.application.usecase;

import com.portability.application.GetOperationStatusService;
import com.portability.domain.model.AuditEvent;
import com.portability.domain.model.AuditEventType;
import com.portability.domain.model.CentralOperationStatus;
import com.portability.domain.model.ConcurrentOperationException;
import com.portability.domain.model.InvalidRequestException;
import com.portability.domain.model.OperationStatus;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.ports.out.CentralUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GetOperationStatusServiceTest {

    private static final String OPERATION_ID = "OPE-1";
    private static final String PROTOCOL = "PRT-OLD";
    private static final String REQUESTED_AT = "2026-11-22T12:34:56Z";
    private static final String NOW = "2026-11-22T12:35:00Z";

    private TestDoubles.State state;
    private TestDoubles.Operations operations;
    private TestDoubles.AuditEvents auditEvents;
    private TestDoubles.Central central;
    private TestDoubles.Backoff backoff;
    private TestDoubles.Metrics metrics;
    private GetOperationStatusService service;

    @BeforeEach
    void setUp() {
        state = new TestDoubles.State();
        operations = new TestDoubles.Operations(state);
        auditEvents = new TestDoubles.AuditEvents(state);
        central = new TestDoubles.Central();
        backoff = new TestDoubles.Backoff();
        metrics = new TestDoubles.Metrics();
        service = new GetOperationStatusService(operations, auditEvents, central, backoff,
                new TestDoubles.Transactions(state), TestDoubles.fixedClock(NOW),
                new TestDoubles.Ids(), metrics);
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void mustPollWithBackoffAndPersistConfirmationWithAuditEvent() {
        operations.seed(pending(), "key");
        central.statuses.add(pendingStatus(Duration.ofSeconds(1)));
        central.statuses.add(pendingStatus(Duration.ofSeconds(1)));
        central.statuses.add(new CentralOperationStatus(OperationStatus.CONFIRMED, null, Duration.ZERO));

        Optional<PortabilityOperation> result = service.getOperationStatus(OPERATION_ID);

        assertTrue(result.isPresent());
        assertEquals(OperationStatus.CONFIRMED, result.get().status());
        assertEquals(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2)), backoff.delays);
        assertEquals(1, auditEvents.findByOperationId(OPERATION_ID).size());
        AuditEvent event = auditEvents.findByOperationId(OPERATION_ID).getFirst();
        assertEquals(AuditEventType.CONFIRMED.externalValue(), event.eventType());
        assertTrue(event.message().contains(PROTOCOL));
        assertTrue(metrics.names.contains("operation.confirmed"));
    }

    @Test
    void mustNotConsultCentralWhenOperationIsAlreadyFinal() {
        operations.seed(terminal(OperationStatus.CONFIRMED), "key");

        PortabilityOperation result = service.getOperationStatus(OPERATION_ID).orElseThrow();

        assertEquals(OperationStatus.CONFIRMED, result.status());
        assertTrue(backoff.delays.isEmpty());
        assertTrue(state.auditEvents.isEmpty());
    }

    @Test
    void mustPersistRejectionReasonAndEventWithProtocol() {
        operations.seed(pending(), "key");
        central.statuses.add(new CentralOperationStatus(OperationStatus.REJECTED,
                "Convênio bloqueado", Duration.ZERO));

        PortabilityOperation result = service.getOperationStatus(OPERATION_ID).orElseThrow();

        assertEquals(OperationStatus.REJECTED, result.status());
        assertEquals("Convênio bloqueado", result.rejectionReason());
        AuditEvent event = auditEvents.findByOperationId(OPERATION_ID).getFirst();
        assertEquals(AuditEventType.REJECTED.externalValue(), event.eventType());
        assertTrue(event.message().contains(PROTOCOL));
        assertTrue(event.message().contains("Convênio bloqueado"));
    }

    @Test
    void mustKeepOperationPendingAfterExhaustingAttempts() {
        operations.seed(pending(), "key");
        central.statuses.add(pendingStatus(Duration.ofSeconds(3)));
        central.statuses.add(pendingStatus(Duration.ofSeconds(3)));
        central.statuses.add(pendingStatus(Duration.ofSeconds(3)));

        PortabilityOperation result = service.getOperationStatus(OPERATION_ID).orElseThrow();

        assertEquals(OperationStatus.PENDING, result.status());
        assertEquals(List.of(Duration.ofSeconds(3), Duration.ofSeconds(3)), backoff.delays);
        assertTrue(state.auditEvents.isEmpty());
        assertTrue(metrics.names.contains("operation.still-pending"));
    }

    @Test
    void mustRecordFailureEventWithoutChangingOperationWhenCentralFails() {
        operations.seed(pending(), "key");
        central.statusFailure = TestDoubles.centralFailure("Central indisponível");

        assertThrows(CentralUnavailableException.class, () -> service.getOperationStatus(OPERATION_ID));

        assertEquals(OperationStatus.PENDING, operations.findById(OPERATION_ID).orElseThrow().status());
        AuditEvent event = auditEvents.findByOperationId(OPERATION_ID).getFirst();
        assertEquals(AuditEventType.INTEGRATION_FAILURE.externalValue(), event.eventType());
        assertTrue(event.message().contains("Central indisponível"));
        assertTrue(metrics.names.contains("operation.integration-failure"));
    }

    @Test
    void mustPreserveInterruptionWhenBackoffIsInterrupted() {
        operations.seed(pending(), "key");
        central.statuses.add(pendingStatus(Duration.ofSeconds(1)));
        backoff.interrupt = true;

        PortabilityOperation result = service.getOperationStatus(OPERATION_ID).orElseThrow();

        assertEquals(OperationStatus.PENDING, result.status());
        assertTrue(Thread.currentThread().isInterrupted());
        assertTrue(state.auditEvents.isEmpty());
    }

    @Test
    void mustNotAppendTerminalEventTwiceWhenItIsAlreadyRecorded() {
        operations.seed(pending(), "key");
        auditEvents.append(new AuditEvent("EVT-OLD", OPERATION_ID, AuditEventType.CONFIRMED,
                "Evento anterior", Instant.parse(NOW)));
        central.statuses.add(new CentralOperationStatus(OperationStatus.CONFIRMED, null, Duration.ZERO));

        service.getOperationStatus(OPERATION_ID);

        assertEquals(1, auditEvents.findByOperationId(OPERATION_ID).size());
    }

    @Test
    void mustRollBackTransitionWhenSaveReportsConcurrency() {
        operations.seed(pending(), "key");
        central.statuses.add(new CentralOperationStatus(OperationStatus.CONFIRMED, null, Duration.ZERO));
        operations.failOnSave = true;

        assertThrows(ConcurrentOperationException.class, () -> service.getOperationStatus(OPERATION_ID));

        assertEquals(OperationStatus.PENDING, operations.findById(OPERATION_ID).orElseThrow().status());
        assertTrue(state.auditEvents.isEmpty());
    }

    @Test
    void mustReturnEmptyWhenOperationDoesNotExist() {
        assertTrue(service.getOperationStatus("missing").isEmpty());
        assertTrue(state.auditEvents.isEmpty());
    }

    @Test
    void mustRejectBlankOperationIdBeforeConsultingPorts() {
        assertThrows(InvalidRequestException.class, () -> service.getOperationStatus(" "));
        assertTrue(state.auditEvents.isEmpty());
    }

    private static CentralOperationStatus pendingStatus(Duration retryAfter) {
        return new CentralOperationStatus(OperationStatus.PENDING, null, retryAfter);
    }

    private static PortabilityOperation pending() {
        return new PortabilityOperation(OPERATION_ID, "BEN-123", List.of("INS-1"), "Nova Credora S.A.",
                new ReceivingAccount("001", "1234", "56789-0"), OperationStatus.PENDING, PROTOCOL, null,
                Instant.parse(REQUESTED_AT));
    }

    private static PortabilityOperation terminal(OperationStatus status) {
        return new PortabilityOperation(OPERATION_ID, "BEN-123", List.of("INS-1"), "Nova Credora S.A.",
                new ReceivingAccount("001", "1234", "56789-0"), status, PROTOCOL, null,
                Instant.parse(REQUESTED_AT));
    }
}
