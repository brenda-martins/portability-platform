package com.portability.application;

import com.portability.domain.model.Authorization;
import com.portability.domain.model.AuthorizationResult;
import com.portability.domain.model.BeneficiaryNotAuthorizedException;
import com.portability.domain.model.DiscountInstallment;
import com.portability.domain.model.InstallmentStatus;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortabilityApplicationServiceTest {

    private static final String BENEFICIARY_ID = "BEN-123";
    private static final String AGREEMENT_ID = "AGR-001";

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
}
