package com.portability.adapters.in.web;

import com.portability.domain.model.AgreementId;
import com.portability.domain.model.Authorization;
import com.portability.domain.model.AuthorizationResult;
import com.portability.domain.model.BeneficiaryId;
import com.portability.domain.ports.in.RegisterAuthorizationUseCase;
import com.portability.domain.ports.out.CentralUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthorizationControllerContractTest {

    private static final String VALID_BODY =
            "{\"beneficiaryId\":\"BEN-123\",\"agreementId\":\"AGR-001\"}";

    private RegisterAuthorizationUseCase registerAuthorizationUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        registerAuthorizationUseCase = mock(RegisterAuthorizationUseCase.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthorizationController(registerAuthorizationUseCase))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldRespond201WithAuthorizationWhenOptInIsValid() throws Exception {
        when(registerAuthorizationUseCase.registerAuthorization(
                        new BeneficiaryId("BEN-123"), new AgreementId("AGR-001")))
                .thenReturn(new AuthorizationResult(new Authorization(
                        "AUT-1", new BeneficiaryId("BEN-123"), new AgreementId("AGR-001"),
                        Instant.parse("2026-11-22T12:34:56Z")), true));

        mockMvc.perform(post("/api/authorizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorizationId").value("AUT-1"))
                .andExpect(jsonPath("$.beneficiaryId").value("BEN-123"))
                .andExpect(jsonPath("$.agreementId").value("AGR-001"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-22T12:34:56Z"));
    }

    @Test
    void shouldRespond200WithExistingAuthorizationWhenResent() throws Exception {
        when(registerAuthorizationUseCase.registerAuthorization(
                        new BeneficiaryId("BEN-123"), new AgreementId("AGR-001")))
                .thenReturn(new AuthorizationResult(new Authorization(
                        "AUT-OLD", new BeneficiaryId("BEN-123"), new AgreementId("AGR-001"),
                        Instant.parse("2026-01-01T00:00:00Z")), false));

        mockMvc.perform(post("/api/authorizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationId").value("AUT-OLD"));
    }

    @Test
    void shouldRespond400WhenBeneficiaryIsBlank() throws Exception {
        mockMvc.perform(post("/api/authorizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"beneficiaryId\":\" \",\"agreementId\":\"AGR-001\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        verifyNoInteractions(registerAuthorizationUseCase);
    }

    @Test
    void shouldRespond409WhenBeneficiaryAndAgreementPairIsDuplicated() throws Exception {
        when(registerAuthorizationUseCase.registerAuthorization(
                        new BeneficiaryId("BEN-123"), new AgreementId("AGR-001")))
                .thenThrow(new DataIntegrityViolationException("duplicate pair"));

        mockMvc.perform(post("/api/authorizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void shouldRespond502WhenCentralFails() throws Exception {
        when(registerAuthorizationUseCase.registerAuthorization(
                        new BeneficiaryId("BEN-123"), new AgreementId("AGR-001")))
                .thenThrow(new CentralUnavailableException("Central unavailable"));

        mockMvc.perform(post("/api/authorizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Central unavailable"));
    }
}
