package com.portability.application.usecase;

import com.portability.application.UpdateReceivingAccountService;
import com.portability.domain.model.AuditEvent;
import com.portability.domain.model.AuditEventType;
import com.portability.domain.model.BeneficiaryId;
import com.portability.domain.model.ConcurrentOperationException;
import com.portability.domain.model.InvalidRequestException;
import com.portability.domain.model.OperationId;
import com.portability.domain.model.OperationNotConfirmedException;
import com.portability.domain.model.OperationStatus;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.Protocol;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.ports.out.CentralUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateReceivingAccountServiceTest {

    private static final OperationId OPERATION_ID = new OperationId("OPE-1");
    private static final Protocol PROTOCOL = new Protocol("PRT-OLD");
    private static final String REQUESTED_AT = "2026-11-22T12:34:56Z";
    private static final String NOW = "2026-11-22T12:35:00Z";

    private TestDoubles.State state;
    private TestDoubles.Operations operations;
    private TestDoubles.AuditEvents auditEvents;
    private TestDoubles.Central central;
    private TestDoubles.Metrics metrics;
    private UpdateReceivingAccountService service;

    @BeforeEach
    void setUp() {
        state = new TestDoubles.State();
        operations = new TestDoubles.Operations(state);
        auditEvents = new TestDoubles.AuditEvents(state);
        central = new TestDoubles.Central(state);
        metrics = new TestDoubles.Metrics();
        var transactions = new TestDoubles.Transactions(state);
        var ids = new TestDoubles.Ids();
        var clock = TestDoubles.fixedClock(NOW);
        service = new UpdateReceivingAccountService(operations, auditEvents, central, transactions,
                TestDoubles.failureRecorder(auditEvents, transactions, clock, ids, metrics), clock, ids, metrics);
    }

    @Test
    void shouldUpdateAccountCallCentralAndAppendEventWhenConfirmed() {
        operations.seed(operation(OperationStatus.CONFIRMED), "key");
        ReceivingAccount newAccount = new ReceivingAccount("002", "4321", "98765-4");

        PortabilityOperation result = service.updateReceivingAccount(OPERATION_ID, newAccount).orElseThrow();

        assertEquals(newAccount, result.receivingAccount());
        assertEquals(List.of(newAccount), central.accountUpdates);
        AuditEvent event = auditEvents.findByOperationId(OPERATION_ID).getFirst();
        assertEquals(AuditEventType.ACCOUNT_UPDATED.externalValue(), event.eventType());
        assertTrue(event.message().startsWith("Receiving account updated for protocol " + PROTOCOL.value() + ": "));
        assertTrue(event.message().contains("98765-4"));
        assertTrue(metrics.names.contains("operation.account-updated"));
    }

    @Test
    void shouldNotCallCentralOrAppendEventWhenAccountIsUnchanged() {
        ReceivingAccount original = new ReceivingAccount("001", "1234", "56789-0");
        operations.seed(operation(OperationStatus.CONFIRMED), "key");

        PortabilityOperation result = service.updateReceivingAccount(OPERATION_ID, original).orElseThrow();

        assertEquals(original, result.receivingAccount());
        assertTrue(central.accountUpdates.isEmpty());
        assertTrue(state.auditEvents.isEmpty());
    }

    @Test
    void shouldRejectAccountUpdateWhenOperationIsPending() {
        operations.seed(operation(OperationStatus.PENDING), "key");

        OperationNotConfirmedException error = assertThrows(OperationNotConfirmedException.class,
                () -> service.updateReceivingAccount(OPERATION_ID, newAccount()));
        assertEquals("Only confirmed operations may have the receiving account changed", error.getMessage());
        assertTrue(central.accountUpdates.isEmpty());
        assertTrue(state.auditEvents.isEmpty());
    }

    @Test
    void shouldRejectAccountUpdateWhenOperationIsRejected() {
        operations.seed(operation(OperationStatus.REJECTED), "key");

        assertThrows(OperationNotConfirmedException.class,
                () -> service.updateReceivingAccount(OPERATION_ID, newAccount()));
        assertTrue(central.accountUpdates.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenOperationDoesNotExist() {
        assertTrue(service.updateReceivingAccount(new OperationId("missing"), newAccount()).isEmpty());
        assertTrue(state.auditEvents.isEmpty());
    }

    @Test
    void shouldRecordFailureEventWithoutChangingAccountWhenCentralFails() {
        operations.seed(operation(OperationStatus.CONFIRMED), "key");
        central.accountFailure = TestDoubles.centralFailure("Central unavailable");

        assertThrows(CentralUnavailableException.class,
                () -> service.updateReceivingAccount(OPERATION_ID, newAccount()));

        assertEquals(new ReceivingAccount("001", "1234", "56789-0"),
                operations.findById(OPERATION_ID).orElseThrow().receivingAccount());
        AuditEvent event = auditEvents.findByOperationId(OPERATION_ID).getFirst();
        assertEquals(AuditEventType.INTEGRATION_FAILURE.externalValue(), event.eventType());
        assertTrue(event.message().contains("Failed to update receiving account at Central"));
    }

    @Test
    void shouldRollBackAccountChangeAndEventWhenSaveReportsConcurrency() {
        operations.seed(operation(OperationStatus.CONFIRMED), "key");
        operations.failOnSave = true;

        assertThrows(ConcurrentOperationException.class,
                () -> service.updateReceivingAccount(OPERATION_ID, newAccount()));

        assertEquals(new ReceivingAccount("001", "1234", "56789-0"),
                operations.findById(OPERATION_ID).orElseThrow().receivingAccount());
        assertTrue(state.auditEvents.isEmpty());
    }

    @Test
    void shouldRejectBlankAccountBeforeTouchingOperationOrCentral() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.updateReceivingAccount(OPERATION_ID,
                        new ReceivingAccount("001", " ", "56789-0")));

        assertEquals("agency must not be blank", error.fields().get("agency"));
        assertTrue(state.auditEvents.isEmpty());
    }

    private static PortabilityOperation operation(OperationStatus status) {
        return new PortabilityOperation(OPERATION_ID, new BeneficiaryId("BEN-123"), List.of("INS-1"),
                "Nova Credora S.A.", new ReceivingAccount("001", "1234", "56789-0"), status, PROTOCOL,
                null, Instant.parse(REQUESTED_AT));
    }

    private static ReceivingAccount newAccount() {
        return new ReceivingAccount("002", "4321", "98765-4");
    }
}
