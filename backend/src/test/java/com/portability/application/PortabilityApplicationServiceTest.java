package com.portability.application;

import com.portability.domain.model.AuditEvent;
import com.portability.domain.model.Authorization;
import com.portability.domain.model.AuthorizationResult;
import com.portability.domain.model.BeneficiaryNotAuthorizedException;
import com.portability.domain.model.DiscountInstallment;
import com.portability.domain.model.InstallmentStatus;
import com.portability.domain.model.OperationStatus;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.PortabilityOperationResult;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.ports.out.AuditEventRepository;
import com.portability.domain.ports.out.AuthorizationRepository;
import com.portability.domain.ports.out.CentralAuthorizationPort;
import com.portability.domain.ports.out.CentralPortabilityPort;
import com.portability.domain.ports.out.CentralUnavailableException;
import com.portability.domain.ports.out.DiscountAgendaPort;
import com.portability.domain.ports.out.OperationRepository;
import com.portability.domain.ports.out.SignaturePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortabilityApplicationServiceTest {

    private static final String BENEFICIARY_ID = "BEN-123";
    private static final String AGREEMENT_ID = "AGR-001";
    private static final String CREDITOR = "Nova Credora S.A.";
    private static final String FUTURE_DATE = "2099-01-10";

    @Mock
    private AuthorizationRepository authorizationRepository;
    @Mock
    private OperationRepository operationRepository;
    @Mock
    private AuditEventRepository auditEventRepository;
    @Mock
    private DiscountAgendaPort discountAgendaPort;
    @Mock
    private CentralPortabilityPort centralPortabilityPort;
    @Mock
    private CentralAuthorizationPort centralAuthorizationPort;
    @Mock
    private SignaturePort signaturePort;

    @InjectMocks
    private PortabilityApplicationService service;

    @Test
    void shouldRegisterAuthorizationWhenBeneficiaryAndPlanAreValid() {
        when(authorizationRepository.findByBeneficiaryAndAgreement(BENEFICIARY_ID, AGREEMENT_ID))
                .thenReturn(Optional.empty());
        when(centralAuthorizationPort.registerAuthorization(BENEFICIARY_ID, AGREEMENT_ID))
                .thenReturn("AUT-1");
        when(authorizationRepository.save(any(Authorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuthorizationResult result = service.registerAuthorization(BENEFICIARY_ID, AGREEMENT_ID);

        assertTrue(result.created());
        assertEquals("AUT-1", result.authorization().authorizationId());
        assertEquals(BENEFICIARY_ID, result.authorization().beneficiaryId());
        assertEquals(AGREEMENT_ID, result.authorization().agreementId());
        assertNotNull(result.authorization().createdAt());
        verify(centralAuthorizationPort).registerAuthorization(BENEFICIARY_ID, AGREEMENT_ID);
    }

    @Test
    void mustReturnExistingAuthorizationWhenSamePairResent() {
        Authorization existing = new Authorization(
                "AUT-OLD", BENEFICIARY_ID, AGREEMENT_ID, Instant.parse("2026-01-01T00:00:00Z"));
        when(authorizationRepository.findByBeneficiaryAndAgreement(BENEFICIARY_ID, AGREEMENT_ID))
                .thenReturn(Optional.of(existing));

        AuthorizationResult result = service.registerAuthorization(BENEFICIARY_ID, AGREEMENT_ID);

        assertFalse(result.created());
        assertSame(existing, result.authorization());
        verifyNoInteractions(centralAuthorizationPort, signaturePort);
        verify(authorizationRepository, never()).save(any());
    }

    @Test
    void mustRejectBeneficiaryEmBrancoWithoutChamarCentral() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registerAuthorization(" ", AGREEMENT_ID));

        verifyNoInteractions(authorizationRepository, centralAuthorizationPort, signaturePort);
    }

    @Test
    void shouldNotPersistWhenCentralFails() {
        when(authorizationRepository.findByBeneficiaryAndAgreement(BENEFICIARY_ID, AGREEMENT_ID))
                .thenReturn(Optional.empty());
        when(centralAuthorizationPort.registerAuthorization(BENEFICIARY_ID, AGREEMENT_ID))
                .thenThrow(new CentralUnavailableException("Central indisponível"));

        CentralUnavailableException error = assertThrows(CentralUnavailableException.class,
                () -> service.registerAuthorization(BENEFICIARY_ID, AGREEMENT_ID));

        assertEquals("Central indisponível", error.getMessage());
        verify(authorizationRepository, never()).save(any());
    }

    @Test
    void shouldReturnFullAgendaWhenBeneficiaryIsAuthorized() {
        DiscountInstallment open = new DiscountInstallment("INS-1", AGREEMENT_ID,
                new BigDecimal("120.50"), LocalDate.parse("2099-01-10"), InstallmentStatus.OPEN);
        DiscountInstallment ported = new DiscountInstallment("INS-3", AGREEMENT_ID,
                new BigDecimal("60.00"), LocalDate.parse("2099-02-10"), InstallmentStatus.PORTED);
        when(authorizationRepository.existsByBeneficiaryId(BENEFICIARY_ID)).thenReturn(true);
        when(discountAgendaPort.fetchAgenda(BENEFICIARY_ID)).thenReturn(List.of(open, ported));

        List<DiscountInstallment> agenda = service.getAgenda(BENEFICIARY_ID);

        assertEquals(List.of(open, ported), agenda);
        verify(discountAgendaPort).fetchAgenda(BENEFICIARY_ID);
    }

    @Test
    void mustRejectAgendaWhenBeneficiaryIsNotAuthorized() {
        when(authorizationRepository.existsByBeneficiaryId(BENEFICIARY_ID)).thenReturn(false);

        assertThrows(BeneficiaryNotAuthorizedException.class, () -> service.getAgenda(BENEFICIARY_ID));

        verifyNoInteractions(discountAgendaPort);
    }

    @Test
    void mustRejectBlankBeneficiaryWithoutCallingCentralForAgenda() {
        assertThrows(IllegalArgumentException.class, () -> service.getAgenda(" "));

        verifyNoInteractions(authorizationRepository, discountAgendaPort);
    }

    @Test
    void mustPropagateCentralFailureWithoutPartialAgenda() {
        when(authorizationRepository.existsByBeneficiaryId(BENEFICIARY_ID)).thenReturn(true);
        when(discountAgendaPort.fetchAgenda(BENEFICIARY_ID))
                .thenThrow(new CentralUnavailableException("Central indisponível"));

        assertThrows(CentralUnavailableException.class, () -> service.getAgenda(BENEFICIARY_ID));
    }

    @Test
    void shouldCreatePendingOperationWithAuditEventWhenSelectionIsEligible() {
        stubAuthorizationAndAgenda(List.of(openInstallment("INS-1", FUTURE_DATE)));
        stubCentralSuccess(List.of("INS-1"));

        PortabilityOperationResult result = service.startPortability(
                BENEFICIARY_ID, List.of("INS-1"), CREDITOR, account());

        assertTrue(result.created());
        assertEquals("PRT-1", result.operation().protocol());
        assertEquals(OperationStatus.PENDING, result.operation().status());
        assertEquals(List.of("INS-1"), result.operation().installmentIds());
        assertNotNull(result.operation().operationId());
        assertNotNull(result.operation().requestedAt());
        verify(centralPortabilityPort).registerPortability(
                BENEFICIARY_ID, List.of("INS-1"), CREDITOR, account());

        ArgumentCaptor<PortabilityOperation> operationCaptor = ArgumentCaptor.forClass(PortabilityOperation.class);
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(operationRepository).save(operationCaptor.capture(), anyString());
        verify(auditEventRepository).append(eventCaptor.capture());
        assertEquals("SOLICITADA", eventCaptor.getValue().eventType());
        assertEquals(operationCaptor.getValue().operationId(), eventCaptor.getValue().operationId());
        assertTrue(eventCaptor.getValue().message().contains("PRT-1"));
    }

    @Test
    void mustReturnExistingOperationWhenSameRequestIsResent() {
        PortabilityOperation existing = operation("OPE-OLD");
        when(operationRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.of(existing));

        PortabilityOperationResult result = service.startPortability(
                BENEFICIARY_ID, List.of("INS-1"), CREDITOR, account());

        assertFalse(result.created());
        assertSame(existing, result.operation());
        verifyNoInteractions(centralPortabilityPort, discountAgendaPort, auditEventRepository,
                authorizationRepository);
        verify(operationRepository, never()).save(any(), anyString());
    }

    @Test
    void mustUseSameIdempotencyKeyRegardlessOfInstallmentOrder() {
        List<DiscountInstallment> agenda = List.of(
                openInstallment("INS-1", FUTURE_DATE), openInstallment("INS-2", FUTURE_DATE));
        when(operationRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(authorizationRepository.existsByBeneficiaryId(BENEFICIARY_ID)).thenReturn(true);
        when(discountAgendaPort.fetchAgenda(BENEFICIARY_ID)).thenReturn(agenda);
        when(centralPortabilityPort.registerPortability(
                anyString(), anyList(), anyString(), any(ReceivingAccount.class))).thenReturn("PRT-1");
        when(operationRepository.save(any(PortabilityOperation.class), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(auditEventRepository.append(any(AuditEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.startPortability(BENEFICIARY_ID, List.of("INS-1", "INS-2"), CREDITOR, account());
        service.startPortability(BENEFICIARY_ID, List.of("INS-2", "INS-1"), CREDITOR, account());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(operationRepository, times(2)).findByIdempotencyKey(keyCaptor.capture());
        assertEquals(keyCaptor.getAllValues().get(0), keyCaptor.getAllValues().get(1));
    }

    @Test
    void mustRejectOperationWhenBeneficiaryIsNotAuthorized() {
        when(operationRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(authorizationRepository.existsByBeneficiaryId(BENEFICIARY_ID)).thenReturn(false);

        assertThrows(BeneficiaryNotAuthorizedException.class, () -> service.startPortability(
                BENEFICIARY_ID, List.of("INS-1"), CREDITOR, account()));

        verifyNoInteractions(centralPortabilityPort, discountAgendaPort, auditEventRepository);
        verify(operationRepository, never()).save(any(), anyString());
    }

    @Test
    void mustRejectInstallmentThatIsNotInAgenda() {
        stubAuthorizationAndAgenda(List.of(openInstallment("INS-1", FUTURE_DATE)));

        assertThrows(IllegalArgumentException.class, () -> service.startPortability(
                BENEFICIARY_ID, List.of("INS-9"), CREDITOR, account()));

        verifyNoInteractions(centralPortabilityPort, auditEventRepository);
        verify(operationRepository, never()).save(any(), anyString());
    }

    @Test
    void mustRejectInstallmentThatWasAlreadyPorted() {
        DiscountInstallment ported = new DiscountInstallment("INS-1", AGREEMENT_ID,
                new BigDecimal("120.50"), LocalDate.parse(FUTURE_DATE), InstallmentStatus.PORTED);
        stubAuthorizationAndAgenda(List.of(ported));

        assertThrows(IllegalArgumentException.class, () -> service.startPortability(
                BENEFICIARY_ID, List.of("INS-1"), CREDITOR, account()));

        verifyNoInteractions(centralPortabilityPort, auditEventRepository);
    }

    @Test
    void mustRejectInstallmentWithPastDiscountDate() {
        stubAuthorizationAndAgenda(List.of(openInstallment("INS-1", "2020-01-10")));

        assertThrows(IllegalArgumentException.class, () -> service.startPortability(
                BENEFICIARY_ID, List.of("INS-1"), CREDITOR, account()));

        verifyNoInteractions(centralPortabilityPort, auditEventRepository);
    }

    @Test
    void mustRejectDuplicatedInstallmentIds() {
        assertThrows(IllegalArgumentException.class, () -> service.startPortability(
                BENEFICIARY_ID, List.of("INS-1", "INS-1"), CREDITOR, account()));

        verifyNoInteractions(operationRepository, authorizationRepository, discountAgendaPort,
                centralPortabilityPort, auditEventRepository);
    }

    @Test
    void mustRejectEmptySelectionWithoutCallingCentral() {
        assertThrows(IllegalArgumentException.class, () -> service.startPortability(
                BENEFICIARY_ID, List.of(), CREDITOR, account()));

        verifyNoInteractions(operationRepository, authorizationRepository, discountAgendaPort,
                centralPortabilityPort, auditEventRepository);
    }

    @Test
    void mustRejectBlankCreditorWithoutCallingCentral() {
        assertThrows(IllegalArgumentException.class, () -> service.startPortability(
                BENEFICIARY_ID, List.of("INS-1"), " ", account()));

        verifyNoInteractions(operationRepository, authorizationRepository, discountAgendaPort,
                centralPortabilityPort, auditEventRepository);
    }

    @Test
    void mustRejectIncompleteReceivingAccountWithoutCallingCentral() {
        assertThrows(IllegalArgumentException.class, () -> service.startPortability(
                BENEFICIARY_ID, List.of("INS-1"), CREDITOR, new ReceivingAccount("001", " ", "56789-0")));

        verifyNoInteractions(operationRepository, authorizationRepository, discountAgendaPort,
                centralPortabilityPort, auditEventRepository);
    }

    @Test
    void mustNotPersistWhenCentralFails() {
        stubAuthorizationAndAgenda(List.of(openInstallment("INS-1", FUTURE_DATE)));
        when(centralPortabilityPort.registerPortability(
                BENEFICIARY_ID, List.of("INS-1"), CREDITOR, account()))
                .thenThrow(new CentralUnavailableException("Central indisponível"));

        assertThrows(CentralUnavailableException.class, () -> service.startPortability(
                BENEFICIARY_ID, List.of("INS-1"), CREDITOR, account()));

        verify(operationRepository, never()).save(any(), anyString());
        verifyNoInteractions(auditEventRepository);
    }

    private void stubAuthorizationAndAgenda(List<DiscountInstallment> agenda) {
        when(operationRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(authorizationRepository.existsByBeneficiaryId(BENEFICIARY_ID)).thenReturn(true);
        when(discountAgendaPort.fetchAgenda(BENEFICIARY_ID)).thenReturn(agenda);
    }

    private void stubCentralSuccess(List<String> installmentIds) {
        when(centralPortabilityPort.registerPortability(BENEFICIARY_ID, installmentIds, CREDITOR, account()))
                .thenReturn("PRT-1");
        when(operationRepository.save(any(PortabilityOperation.class), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(auditEventRepository.append(any(AuditEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static ReceivingAccount account() {
        return new ReceivingAccount("001", "1234", "56789-0");
    }

    private static DiscountInstallment openInstallment(String id, String date) {
        return new DiscountInstallment(id, AGREEMENT_ID, new BigDecimal("120.50"),
                LocalDate.parse(date), InstallmentStatus.OPEN);
    }

    private static PortabilityOperation operation(String operationId) {
        return new PortabilityOperation(operationId, BENEFICIARY_ID, List.of("INS-1"), CREDITOR,
                account(), OperationStatus.PENDING, "PRT-OLD", null,
                Instant.parse("2026-11-22T12:34:56Z"));
    }
}
