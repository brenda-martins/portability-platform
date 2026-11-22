package com.portability.adapters.in.web;

import com.portability.domain.model.BeneficiaryNotAuthorizedException;
import com.portability.domain.model.OperationStatus;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.PortabilityOperationResult;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.ports.in.StartPortabilityUseCase;
import com.portability.domain.ports.out.CentralUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PortabilityControllerContractTest {

    private static final String VALID_BODY =
            "{\"beneficiaryId\":\"BEN-123\",\"installmentIds\":[\"INS-1\"],"
                    + "\"creditorAssignee\":\"Nova Credora S.A.\","
                    + "\"receivingAccount\":{\"bank\":\"001\",\"agency\":\"1234\",\"account\":\"56789-0\"}}";

    private StartPortabilityUseCase startPortabilityUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        startPortabilityUseCase = mock(StartPortabilityUseCase.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new PortabilityController(startPortabilityUseCase))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldRespond201WithOperationWhenRequestIsNew() throws Exception {
        when(startPortabilityUseCase.startPortability("BEN-123", List.of("INS-1"),
                "Nova Credora S.A.", new ReceivingAccount("001", "1234", "56789-0")))
                .thenReturn(new PortabilityOperationResult(operation(), true));

        mockMvc.perform(post("/api/portability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operationId").value("OPE-1"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.protocol").value("PRT-1"))
                .andExpect(jsonPath("$.installmentIds[0]").value("INS-1"));
    }

    @Test
    void shouldRespond200WithExistingOperationWhenResent() throws Exception {
        when(startPortabilityUseCase.startPortability("BEN-123", List.of("INS-1"),
                "Nova Credora S.A.", new ReceivingAccount("001", "1234", "56789-0")))
                .thenReturn(new PortabilityOperationResult(operation(), false));

        mockMvc.perform(post("/api/portability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value("OPE-1"));
    }

    @Test
    void shouldRespond400WhenBeneficiaryIsBlank() throws Exception {
        mockMvc.perform(post("/api/portability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"BEN-123\"", "\" \"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        verifyNoInteractions(startPortabilityUseCase);
    }

    @Test
    void shouldRespond400WhenSelectionIsEmpty() throws Exception {
        mockMvc.perform(post("/api/portability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("[\"INS-1\"]", "[]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        verifyNoInteractions(startPortabilityUseCase);
    }

    @Test
    void shouldRespond400WhenReceivingAccountIsIncomplete() throws Exception {
        mockMvc.perform(post("/api/portability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"bank\":\"001\"", "\"bank\":\"\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        verifyNoInteractions(startPortabilityUseCase);
    }

    @Test
    void mustRespond403WhenBeneficiaryIsNotAuthorized() throws Exception {
        when(startPortabilityUseCase.startPortability("BEN-123", List.of("INS-1"),
                "Nova Credora S.A.", new ReceivingAccount("001", "1234", "56789-0")))
                .thenThrow(new BeneficiaryNotAuthorizedException("Beneficiário não autorizado"));

        mockMvc.perform(post("/api/portability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Beneficiário não autorizado"));
    }

    @Test
    void mustRespond502WhenCentralFails() throws Exception {
        when(startPortabilityUseCase.startPortability("BEN-123", List.of("INS-1"),
                "Nova Credora S.A.", new ReceivingAccount("001", "1234", "56789-0")))
                .thenThrow(new CentralUnavailableException("Central indisponível"));

        mockMvc.perform(post("/api/portability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Central indisponível"));
    }

    private static PortabilityOperation operation() {
        return new PortabilityOperation("OPE-1", "BEN-123", List.of("INS-1"),
                "Nova Credora S.A.", new ReceivingAccount("001", "1234", "56789-0"),
                OperationStatus.PENDING, "PRT-1", null,
                Instant.parse("2026-11-22T12:34:56Z"));
    }
}
