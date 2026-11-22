package com.portability.adapters.in.web;

import com.portability.domain.model.AgreementId;
import com.portability.domain.model.BeneficiaryId;
import com.portability.domain.model.BeneficiaryNotAuthorizedException;
import com.portability.domain.model.DiscountInstallment;
import com.portability.domain.model.InstallmentStatus;
import com.portability.domain.ports.in.GetDiscountAgendaUseCase;
import com.portability.domain.ports.out.CentralUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgendaControllerContractTest {

    private GetDiscountAgendaUseCase getDiscountAgendaUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        getDiscountAgendaUseCase = mock(GetDiscountAgendaUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AgendaController(getDiscountAgendaUseCase))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldRespond200WithInstallmentsWhenBeneficiaryIsAuthorized() throws Exception {
        when(getDiscountAgendaUseCase.getAgenda(new BeneficiaryId("BEN-123"))).thenReturn(List.of(
                new DiscountInstallment("INS-1", new AgreementId("AGR-001"), new BigDecimal("120.50"),
                        LocalDate.parse("2099-01-10"), InstallmentStatus.OPEN)));

        mockMvc.perform(get("/api/beneficiaries/BEN-123/agenda"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].installmentId").value("INS-1"))
                .andExpect(jsonPath("$[0].agreementId").value("AGR-001"))
                .andExpect(jsonPath("$[0].amount").value(120.5))
                .andExpect(jsonPath("$[0].discountDate").value("2099-01-10"))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    void mustRespond403WhenBeneficiaryIsNotAuthorized() throws Exception {
        when(getDiscountAgendaUseCase.getAgenda(new BeneficiaryId("BEN-999")))
                .thenThrow(new BeneficiaryNotAuthorizedException("Beneficiary not authorized"));

        mockMvc.perform(get("/api/beneficiaries/BEN-999/agenda"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Beneficiary not authorized"));
    }

    @Test
    void shouldRespond400WhenBeneficiaryIdIsBlank() throws Exception {
        mockMvc.perform(get("/api/beneficiaries/{beneficiaryId}/agenda", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.fields.beneficiaryId").value("beneficiaryId must not be blank"));
    }

    @Test
    void mustRespond502WhenCentralFails() throws Exception {
        when(getDiscountAgendaUseCase.getAgenda(new BeneficiaryId("BEN-123")))
                .thenThrow(new CentralUnavailableException("Central unavailable"));

        mockMvc.perform(get("/api/beneficiaries/BEN-123/agenda"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Central unavailable"));
    }
}
