package com.portability.adapters.in.web;

import com.portability.domain.model.BeneficiaryId;
import com.portability.domain.model.BeneficiaryNotAuthorizedException;
import com.portability.domain.model.OperationId;
import com.portability.domain.model.OperationNotConfirmedException;
import com.portability.domain.model.OperationStatus;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.PortabilityOperationResult;
import com.portability.domain.model.Protocol;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.model.StartPortabilityCommand;
import com.portability.domain.ports.in.GetOperationStatusUseCase;
import com.portability.domain.ports.in.StartPortabilityUseCase;
import com.portability.domain.ports.in.UpdateReceivingAccountUseCase;
import com.portability.domain.ports.out.CentralUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PortabilityControllerContractTest {

    private static final String VALID_BODY =
            "{\"beneficiaryId\":\"BEN-123\",\"installmentIds\":[\"INS-1\"],"
                    + "\"creditorAssignee\":\"Nova Credora S.A.\","
                    + "\"receivingAccount\":{\"bank\":\"001\",\"agency\":\"1234\",\"account\":\"56789-0\"}}";

    private static final String ACCOUNT_BODY =
            "{\"bank\":\"002\",\"agency\":\"4321\",\"account\":\"98765-4\"}";

    private StartPortabilityUseCase startPortabilityUseCase;
    private GetOperationStatusUseCase getOperationStatusUseCase;
    private UpdateReceivingAccountUseCase updateReceivingAccountUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        startPortabilityUseCase = mock(StartPortabilityUseCase.class);
        getOperationStatusUseCase = mock(GetOperationStatusUseCase.class);
        updateReceivingAccountUseCase = mock(UpdateReceivingAccountUseCase.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new PortabilityController(
                startPortabilityUseCase, getOperationStatusUseCase, updateReceivingAccountUseCase))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldRespond201WithOperationWhenRequestIsNew() throws Exception {
        when(startPortabilityUseCase.startPortability(command()))
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
        when(startPortabilityUseCase.startPortability(command()))
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
        when(startPortabilityUseCase.startPortability(command()))
                .thenThrow(new BeneficiaryNotAuthorizedException("Beneficiary not authorized"));

        mockMvc.perform(post("/api/portability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Beneficiary not authorized"));
    }

    @Test
    void mustRespond502WhenCentralFails() throws Exception {
        when(startPortabilityUseCase.startPortability(command()))
                .thenThrow(new CentralUnavailableException("Central unavailable"));

        mockMvc.perform(post("/api/portability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Central unavailable"));
    }

    @Test
    void shouldRespond200WithOperationWhenStatusIsConsulted() throws Exception {
        when(getOperationStatusUseCase.getOperationStatus(new OperationId("OPE-1"))).thenReturn(Optional.of(operation()));

        mockMvc.perform(get("/api/portability/OPE-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value("OPE-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void shouldRespond404WhenOperationDoesNotExist() throws Exception {
        when(getOperationStatusUseCase.getOperationStatus(new OperationId("missing"))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/portability/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void mustRespond502WhenCentralFailsDuringStatusConsultation() throws Exception {
        when(getOperationStatusUseCase.getOperationStatus(new OperationId("OPE-1")))
                .thenThrow(new CentralUnavailableException("Central unavailable"));

        mockMvc.perform(get("/api/portability/OPE-1"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Central unavailable"));
    }

    @Test
    void mustRespond409WhenConcurrentUpdateWins() throws Exception {
        when(getOperationStatusUseCase.getOperationStatus(new OperationId("OPE-1")))
                .thenThrow(new OptimisticLockingFailureException("stale version"));

        mockMvc.perform(get("/api/portability/OPE-1"))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRespond200WithUpdatedAccountWhenOperationIsConfirmed() throws Exception {
        ReceivingAccount newAccount = new ReceivingAccount("002", "4321", "98765-4");
        PortabilityOperation updated = new PortabilityOperation(new OperationId("OPE-1"),
                new BeneficiaryId("BEN-123"), List.of("INS-1"), "Nova Credora S.A.", newAccount,
                OperationStatus.CONFIRMED, new Protocol("PRT-1"), null,
                Instant.parse("2026-11-22T12:34:56Z"));
        when(updateReceivingAccountUseCase.updateReceivingAccount(new OperationId("OPE-1"), newAccount))
                .thenReturn(Optional.of(updated));

        mockMvc.perform(put("/api/portability/OPE-1/receiving-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ACCOUNT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value("OPE-1"))
                .andExpect(jsonPath("$.receivingAccount.bank").value("002"))
                .andExpect(jsonPath("$.receivingAccount.agency").value("4321"))
                .andExpect(jsonPath("$.receivingAccount.account").value("98765-4"));
    }

    @Test
    void shouldRespond400WhenUpdatedAccountIsBlank() throws Exception {
        mockMvc.perform(put("/api/portability/OPE-1/receiving-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ACCOUNT_BODY.replace("\"bank\":\"002\"", "\"bank\":\"\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        verifyNoInteractions(updateReceivingAccountUseCase);
    }

    @Test
    void shouldRespond404WhenOperationToUpdateDoesNotExist() throws Exception {
        when(updateReceivingAccountUseCase.updateReceivingAccount(new OperationId("missing"),
                new ReceivingAccount("002", "4321", "98765-4"))).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/portability/missing/receiving-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ACCOUNT_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void mustRespond409WhenOperationIsNotConfirmed() throws Exception {
        when(updateReceivingAccountUseCase.updateReceivingAccount(new OperationId("OPE-1"),
                new ReceivingAccount("002", "4321", "98765-4")))
                .thenThrow(new OperationNotConfirmedException(
                        "Only confirmed operations may have the receiving account changed"));

        mockMvc.perform(put("/api/portability/OPE-1/receiving-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ACCOUNT_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Only confirmed operations may have the receiving account changed"));
    }

    @Test
    void mustRespond502WhenCentralFailsDuringAccountUpdate() throws Exception {
        when(updateReceivingAccountUseCase.updateReceivingAccount(new OperationId("OPE-1"),
                new ReceivingAccount("002", "4321", "98765-4")))
                .thenThrow(new CentralUnavailableException("Central unavailable"));

        mockMvc.perform(put("/api/portability/OPE-1/receiving-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ACCOUNT_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Central unavailable"));
    }

    private static StartPortabilityCommand command() {
        return new StartPortabilityCommand(new BeneficiaryId("BEN-123"), List.of("INS-1"),
                "Nova Credora S.A.", new ReceivingAccount("001", "1234", "56789-0"));
    }

    private static PortabilityOperation operation() {
        return new PortabilityOperation(new OperationId("OPE-1"), new BeneficiaryId("BEN-123"),
                List.of("INS-1"), "Nova Credora S.A.", new ReceivingAccount("001", "1234", "56789-0"),
                OperationStatus.PENDING, new Protocol("PRT-1"), null,
                Instant.parse("2026-11-22T12:34:56Z"));
    }
}
