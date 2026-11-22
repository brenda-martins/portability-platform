package com.portability.application.usecase;

import com.portability.application.StartPortabilityService;
import com.portability.domain.model.AgreementId;
import com.portability.domain.model.AuditEvent;
import com.portability.domain.model.AuditEventType;
import com.portability.domain.model.Authorization;
import com.portability.domain.model.BeneficiaryId;
import com.portability.domain.model.BeneficiaryNotAuthorizedException;
import com.portability.domain.model.ConcurrentOperationException;
import com.portability.domain.model.DiscountInstallment;
import com.portability.domain.model.InstallmentStatus;
import com.portability.domain.model.InvalidRequestException;
import com.portability.domain.model.OperationId;
import com.portability.domain.model.OperationStatus;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.PortabilityOperationResult;
import com.portability.domain.model.Protocol;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.model.StartPortabilityCommand;
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

    private static final BeneficiaryId BENEFICIARY = new BeneficiaryId("BEN-123");
    private static final AgreementId AGREEMENT = new AgreementId("AGR-001");
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
        central = new TestDoubles.Central(state);
        metrics = new TestDoubles.Metrics();
        service = new StartPortabilityService(operations, authorizations, auditEvents, agenda, central,
                new TestDoubles.Transactions(state), TestDoubles.fixedClock(REQUESTED_AT),
                new TestDoubles.Ids(), metrics);
    }

    @Test
    void mustCreatePendingOperationAndRequestEventWhenSelectionIsEligible() {
        authorize();
        agenda.installments = List.of(openInstallment("INS-1", "2099-01-10"));

        PortabilityOperationResult result = service.startPortability(command(List.of("INS-1")));

        assertTrue(result.created());
        assertEquals(new Protocol("PRT-1"), result.operation().protocol());
        assertEquals(OperationStatus.PENDING, result.operation().status());
        assertEquals(List.of("INS-1"), result.operation().installmentIds());
        assertEquals(Instant.parse(REQUESTED_AT), result.operation().requestedAt());
        assertEquals(1, auditEvents.findByOperationId(result.operation().operationId()).size());
        AuditEvent event = auditEvents.findByOperationId(result.operation().operationId()).getFirst();
        assertEquals(AuditEventType.REQUESTED.externalValue(), event.eventType());
        assertTrue(event.message().startsWith("Operation requested with protocol PRT-1"));
        assertTrue(metrics.names.contains("portability.created"));
    }

    @Test
    void mustReturnExistingOperationWithoutCallingCentralWhenResent() {
        PortabilityOperation existing = pendingOperation(new OperationId("OPE-OLD"));
        operations.seed(existing, PortabilityOperation.idempotencyKey(command(List.of("INS-1"))));

        PortabilityOperationResult result = service.startPortability(command(List.of("INS-1")));

        assertFalse(result.created());
        assertEquals(existing, result.operation());
        assertTrue(state.auditEvents.isEmpty());
        assertTrue(metrics.names.contains("portability.resend"));
    }

    @Test
    void mustReserveIdempotencyKeyBeforeCallingCentral() {
        authorize();
        agenda.installments = List.of(openInstallment("INS-1", "2099-01-10"));

        service.startPortability(command(List.of("INS-1")));

        assertEquals(List.of("reserve", "central"), state.calls);
    }

    @Test
    void mustReturnExistingOperationWithoutCallingCentralWhenReservationConflicts() {
        PortabilityOperation existing = pendingOperation(new OperationId("OPE-OLD"));
        operations.seed(existing, PortabilityOperation.idempotencyKey(command(List.of("INS-1"))));
        operations.hideExistingOnFirstLookup = true;

        PortabilityOperationResult result = service.startPortability(command(List.of("INS-1")));

        assertFalse(result.created());
        assertEquals(existing, result.operation());
        assertEquals(0, central.registrations);
        assertTrue(state.auditEvents.isEmpty());
        assertTrue(metrics.names.contains("portability.resend"));
    }

    @Test
    void mustUseSameIdempotencyKeyRegardlessOfInstallmentOrder() {
        authorize();
        agenda.installments = List.of(openInstallment("INS-1", "2099-01-10"),
                openInstallment("INS-2", "2099-01-10"));

        service.startPortability(command(List.of("INS-1", "INS-2")));
        PortabilityOperationResult second = service.startPortability(command(List.of("INS-2", "INS-1")));

        assertFalse(second.created());
        assertEquals(1, state.operations.size());
    }

    @Test
    void mustRejectOperationWhenBeneficiaryHasNoOptIn() {
        assertThrows(BeneficiaryNotAuthorizedException.class,
                () -> service.startPortability(command(List.of("INS-1"))));

        assertTrue(state.operations.isEmpty());
        assertTrue(state.auditEvents.isEmpty());
    }

    @Test
    void mustRejectInstallmentThatIsNotInAgenda() {
        authorize();
        agenda.installments = List.of(openInstallment("INS-1", "2099-01-10"));

        assertThrows(InvalidRequestException.class,
                () -> service.startPortability(command(List.of("INS-9"))));
        assertTrue(state.operations.isEmpty());
    }

    @Test
    void mustRejectInstallmentThatWasAlreadyPorted() {
        authorize();
        agenda.installments = List.of(new DiscountInstallment("INS-1", AGREEMENT, new BigDecimal("120.50"),
                LocalDate.parse("2099-01-10"), InstallmentStatus.PORTED));

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.startPortability(command(List.of("INS-1"))));
        assertTrue(error.getMessage().startsWith("Ineligible installment: INS-1"));
        assertTrue(state.operations.isEmpty());
    }

    @Test
    void mustRejectInstallmentWithPastDiscountDate() {
        authorize();
        agenda.installments = List.of(openInstallment("INS-1", "2020-01-10"));

        assertThrows(InvalidRequestException.class,
                () -> service.startPortability(command(List.of("INS-1"))));
        assertTrue(state.operations.isEmpty());
    }

    @Test
    void mustRejectDuplicatedInstallmentIdsBeforeCallingPorts() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.startPortability(command(List.of("INS-1", "INS-1"))));

        assertEquals("installmentIds must not contain duplicates", error.fields().get("installmentIds"));
        assertTrue(state.operations.isEmpty());
        assertTrue(metrics.names.isEmpty());
    }

    @Test
    void mustRejectEmptySelectionBeforeCallingPorts() {
        assertThrows(InvalidRequestException.class,
                () -> service.startPortability(command(List.of())));
        assertTrue(state.operations.isEmpty());
    }

    @Test
    void mustRejectBlankCreditorBeforeCallingCentral() {
        assertThrows(InvalidRequestException.class,
                () -> service.startPortability(command(List.of("INS-1"), " ", account())));
        assertTrue(state.operations.isEmpty());
    }

    @Test
    void mustRejectIncompleteReceivingAccountBeforeCallingCentral() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.startPortability(command(List.of("INS-1"), CREDITOR,
                        new ReceivingAccount("001", " ", "56789-0"))));

        assertEquals("agency must not be blank", error.fields().get("agency"));
        assertTrue(state.operations.isEmpty());
    }

    @Test
    void mustNotPersistOperationOrEventWhenCentralFails() {
        authorize();
        agenda.installments = List.of(openInstallment("INS-1", "2099-01-10"));
        central.registrationFailure = TestDoubles.centralFailure("Central unavailable");

        assertThrows(CentralUnavailableException.class,
                () -> service.startPortability(command(List.of("INS-1"))));

        assertTrue(state.operations.isEmpty());
        assertTrue(state.auditEvents.isEmpty());
    }

    @Test
    void mustRollbackOperationAndEventWhenSaveReportsConcurrency() {
        authorize();
        agenda.installments = List.of(openInstallment("INS-1", "2099-01-10"));
        operations.failOnSave = true;

        assertThrows(ConcurrentOperationException.class,
                () -> service.startPortability(command(List.of("INS-1"))));

        assertTrue(state.operations.isEmpty());
        assertTrue(state.auditEvents.isEmpty());
    }

    private void authorize() {
        authorizations.save(new Authorization(
                "AUT-1", BENEFICIARY, AGREEMENT, Instant.parse(REQUESTED_AT)));
    }

    private static StartPortabilityCommand command(List<String> installments) {
        return command(installments, CREDITOR, account());
    }

    private static StartPortabilityCommand command(
            List<String> installments, String creditor, ReceivingAccount account) {
        return new StartPortabilityCommand(BENEFICIARY, installments, creditor, account);
    }

    private static PortabilityOperation pendingOperation(OperationId operationId) {
        return new PortabilityOperation(operationId, BENEFICIARY, List.of("INS-1"), CREDITOR,
                account(), OperationStatus.PENDING, new Protocol("PRT-OLD"), null,
                Instant.parse(REQUESTED_AT), 0L);
    }

    private static ReceivingAccount account() {
        return new ReceivingAccount("001", "1234", "56789-0");
    }

    private static DiscountInstallment openInstallment(String id, String date) {
        return new DiscountInstallment(id, AGREEMENT, new BigDecimal("120.50"),
                LocalDate.parse(date), InstallmentStatus.OPEN);
    }
}
