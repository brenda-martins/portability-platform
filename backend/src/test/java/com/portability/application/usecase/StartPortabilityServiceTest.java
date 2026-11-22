package com.portability.application.usecase;

import com.portability.application.StartPortabilityService;
import com.portability.domain.model.AuditEvent;
import com.portability.domain.model.AuditEventType;
import com.portability.domain.model.BeneficiaryNotAuthorizedException;
import com.portability.domain.model.ConcurrentOperationException;
import com.portability.domain.model.DiscountInstallment;
import com.portability.domain.model.InstallmentStatus;
import com.portability.domain.model.InvalidRequestException;
import com.portability.domain.model.OperationStatus;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.PortabilityOperationResult;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.ports.out.CentralUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartPortabilityServiceTest {

    private static final String BENEFICIARY = "BEN-123";
    private static final String AGREEMENT = "AGR-001";
    private static final String CREDITOR = "Nova Credora S.A.";
    private static final String REQUESTED_AT = "2026-11-22T12:34:56Z";

    private TestDoubles.State state;
    private TestDoubles.Operations operations;
    private TestDoubles.AuditEvents auditEvents;
    private TestDoubles.Authorizations authorizations;
    private TestDoubles.Agenda agenda;
    private TestDoubles.Central central;
    private TestDoubles.Metrics metrics;
    private StartPortabilityService service;

    @BeforeEach
    void setUp() {
        state = new TestDoubles.State();
        operations = new TestDoubles.Operations(state);
        auditEvents = new TestDoubles.AuditEvents(state);
        authorizations = new TestDoubles.Authorizations(state);
        agenda = new TestDoubles.Agenda();
        central = new TestDoubles.Central();
        metrics = new TestDoubles.Metrics();
        service = new StartPortabilityService(operations, authorizations, auditEvents, agenda, central,
                new TestDoubles.Transactions(state), TestDoubles.fixedClock(REQUESTED_AT),
                new TestDoubles.Ids(), metrics);
    }

    @Test
    void mustCreatePendingOperationAndRequestEventWhenSelectionIsEligible() {
        authorize();
        agenda.installments = List.of(openInstallment("INS-1", "2099-01-10"));

        PortabilityOperationResult result = service.startPortability(
                BENEFICIARY, List.of("INS-1"), CREDITOR, account());

        assertTrue(result.created());
        assertEquals("PRT-1", result.operation().protocol());
        assertEquals(OperationStatus.PENDING, result.operation().status());
        assertEquals(List.of("INS-1"), result.operation().installmentIds());
        assertEquals(Instant.parse(REQUESTED_AT), result.operation().requestedAt());
        assertEquals(1, auditEvents.findByOperationId(result.operation().operationId()).size());
        AuditEvent event = auditEvents.findByOperationId(result.operation().operationId()).getFirst();
        assertEquals(AuditEventType.REQUESTED.externalValue(), event.eventType());
        assertTrue(event.message().contains("PRT-1"));
        assertTrue(metrics.names.contains("portability.created"));
    }

    @Test
    void mustReturnExistingOperationWithoutCallingCentralWhenResent() {
        PortabilityOperation existing = pendingOperation("OPE-OLD");
        operations.seed(existing, PortabilityOperation.idempotencyKey(
                BENEFICIARY, List.of("INS-1"), CREDITOR, account()));

        PortabilityOperationResult result = service.startPortability(
                BENEFICIARY, List.of("INS-1"), CREDITOR, account());

        assertFalse(result.created());
        assertEquals(existing, result.operation());
        assertTrue(state.auditEvents.isEmpty());
        assertTrue(metrics.names.contains("portability.resend"));
    }

    @Test
    void mustUseSameIdempotencyKeyRegardlessOfInstallmentOrder() {
        authorize();
        agenda.installments = List.of(openInstallment("INS-1", "2099-01-10"),
                openInstallment("INS-2", "2099-01-10"));

        service.startPortability(BENEFICIARY, List.of("INS-1", "INS-2"), CREDITOR, account());
        PortabilityOperationResult second = service.startPortability(
                BENEFICIARY, List.of("INS-2", "INS-1"), CREDITOR, account());

        assertFalse(second.created());
        assertEquals(1, state.operations.size());
    }

    @Test
    void mustRejectOperationWhenBeneficiaryHasNoOptIn() {
        assertThrows(BeneficiaryNotAuthorizedException.class, () -> service.startPortability(
                BENEFICIARY, List.of("INS-1"), CREDITOR, account()));

        assertTrue(state.operations.isEmpty());
        assertTrue(state.auditEvents.isEmpty());
    }

    @Test
    void mustRejectInstallmentThatIsNotInAgenda() {
        authorize();
        agenda.installments = List.of(openInstallment("INS-1", "2099-01-10"));

        assertThrows(InvalidRequestException.class, () -> service.startPortability(
                BENEFICIARY, List.of("INS-9"), CREDITOR, account()));
        assertTrue(state.operations.isEmpty());
    }

    @Test
    void mustRejectInstallmentThatWasAlreadyPorted() {
        authorize();
        agenda.installments = List.of(new DiscountInstallment("INS-1", AGREEMENT, new BigDecimal("120.50"),
                LocalDate.parse("2099-01-10"), InstallmentStatus.PORTED));

        assertThrows(InvalidRequestException.class, () -> service.startPortability(
                BENEFICIARY, List.of("INS-1"), CREDITOR, account()));
        assertTrue(state.operations.isEmpty());
    }

    @Test
    void mustRejectInstallmentWithPastDiscountDate() {
        authorize();
        agenda.installments = List.of(openInstallment("INS-1", "2020-01-10"));

        assertThrows(InvalidRequestException.class, () -> service.startPortability(
                BENEFICIARY, List.of("INS-1"), CREDITOR, account()));
        assertTrue(state.operations.isEmpty());
    }

    @Test
    void mustRejectDuplicatedInstallmentIdsBeforeCallingPorts() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.startPortability(BENEFICIARY, List.of("INS-1", "INS-1"), CREDITOR, account()));

        assertEquals("installmentIds must not contain duplicates", error.fields().get("installmentIds"));
        assertTrue(state.operations.isEmpty());
        assertTrue(metrics.names.isEmpty());
    }

    @Test
    void mustRejectEmptySelectionBeforeCallingPorts() {
        assertThrows(InvalidRequestException.class,
                () -> service.startPortability(BENEFICIARY, List.of(), CREDITOR, account()));
        assertTrue(state.operations.isEmpty());
    }

    @Test
    void mustRejectBlankCreditorBeforeCallingCentral() {
        assertThrows(InvalidRequestException.class,
                () -> service.startPortability(BENEFICIARY, List.of("INS-1"), " ", account()));
        assertTrue(state.operations.isEmpty());
    }

    @Test
    void mustRejectIncompleteReceivingAccountBeforeCallingCentral() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.startPortability(BENEFICIARY, List.of("INS-1"), CREDITOR,
                        new ReceivingAccount("001", " ", "56789-0")));

        assertEquals("agency must not be blank", error.fields().get("agency"));
        assertTrue(state.operations.isEmpty());
    }

    @Test
    void mustNotPersistOperationOrEventWhenCentralFails() {
        authorize();
        agenda.installments = List.of(openInstallment("INS-1", "2099-01-10"));
        central.registrationFailure = TestDoubles.centralFailure("Central indisponível");

        assertThrows(CentralUnavailableException.class, () -> service.startPortability(
                BENEFICIARY, List.of("INS-1"), CREDITOR, account()));

        assertTrue(state.operations.isEmpty());
        assertTrue(state.auditEvents.isEmpty());
    }

    @Test
    void mustRollbackOperationAndEventWhenSaveReportsConcurrency() {
        authorize();
        agenda.installments = List.of(openInstallment("INS-1", "2099-01-10"));
        operations.failOnSave = true;

        assertThrows(ConcurrentOperationException.class, () -> service.startPortability(
                BENEFICIARY, List.of("INS-1"), CREDITOR, account()));

        assertTrue(state.operations.isEmpty());
        assertTrue(state.auditEvents.isEmpty());
    }

    private void authorize() {
        authorizations.save(new com.portability.domain.model.Authorization(
                "AUT-1", BENEFICIARY, AGREEMENT, Instant.parse(REQUESTED_AT)));
    }

    private static PortabilityOperation pendingOperation(String operationId) {
        return new PortabilityOperation(operationId, BENEFICIARY, List.of("INS-1"), CREDITOR,
                account(), OperationStatus.PENDING, "PRT-OLD", null, Instant.parse(REQUESTED_AT));
    }

    private static ReceivingAccount account() {
        return new ReceivingAccount("001", "1234", "56789-0");
    }

    private static DiscountInstallment openInstallment(String id, String date) {
        return new DiscountInstallment(id, AGREEMENT, new BigDecimal("120.50"),
                LocalDate.parse(date), InstallmentStatus.OPEN);
    }
}
