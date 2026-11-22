package com.portability.application.usecase;

import com.portability.application.GetDiscountAgendaService;
import com.portability.domain.model.BeneficiaryNotAuthorizedException;
import com.portability.domain.model.DiscountInstallment;
import com.portability.domain.model.InstallmentStatus;
import com.portability.domain.model.InvalidRequestException;
import com.portability.domain.ports.out.CentralUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GetDiscountAgendaServiceTest {

    private static final String BENEFICIARY = "BEN-123";

    private TestDoubles.State state;
    private TestDoubles.Authorizations authorizations;
    private TestDoubles.Agenda agenda;
    private TestDoubles.Metrics metrics;
    private GetDiscountAgendaService service;

    @BeforeEach
    void setUp() {
        state = new TestDoubles.State();
        authorizations = new TestDoubles.Authorizations(state);
        agenda = new TestDoubles.Agenda();
        metrics = new TestDoubles.Metrics();
        service = new GetDiscountAgendaService(authorizations, agenda, metrics);
    }

    @Test
    void mustReturnFullAgendaWhenBeneficiaryIsAuthorized() {
        authorize();
        List<DiscountInstallment> installments = List.of(installment("INS-1", InstallmentStatus.OPEN),
                installment("INS-3", InstallmentStatus.PORTED));
        agenda.installments = installments;

        assertEquals(installments, service.getAgenda(BENEFICIARY));
        assertTrue(metrics.names.contains("agenda.consulted"));
    }

    @Test
    void mustRejectAgendaWhenBeneficiaryHasNoOptIn() {
        assertThrows(BeneficiaryNotAuthorizedException.class, () -> service.getAgenda(BENEFICIARY));

        assertTrue(metrics.names.isEmpty());
    }

    @Test
    void mustRejectBlankBeneficiaryBeforeConsultingPorts() {
        assertThrows(InvalidRequestException.class, () -> service.getAgenda(" "));

        assertTrue(metrics.names.isEmpty());
    }

    @Test
    void mustPropagateCentralFailureWithoutPartialAgenda() {
        authorize();
        agenda.failure = TestDoubles.centralFailure("Central indisponível");

        assertThrows(CentralUnavailableException.class, () -> service.getAgenda(BENEFICIARY));
        assertTrue(metrics.names.isEmpty());
    }

    private void authorize() {
        authorizations.save(new com.portability.domain.model.Authorization(
                "AUT-1", BENEFICIARY, "AGR-001", java.time.Instant.parse("2026-11-22T12:34:56Z")));
    }

    private static DiscountInstallment installment(String id, InstallmentStatus status) {
        return new DiscountInstallment(id, "AGR-001", new BigDecimal("120.50"),
                LocalDate.parse("2099-01-10"), status);
    }
}
