package com.portability.adapters.in.web;

import com.portability.domain.model.AuditEvent;
import com.portability.domain.model.OperationId;
import com.portability.domain.ports.in.GetOperationAuditUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditControllerContractTest {

    private GetOperationAuditUseCase getOperationAuditUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        getOperationAuditUseCase = mock(GetOperationAuditUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuditController(getOperationAuditUseCase))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldRespond200WithAuditEventsWhenOperationExists() throws Exception {
        when(getOperationAuditUseCase.getAuditTrail(new OperationId("OPE-1"))).thenReturn(Optional.of(List.of(
                new AuditEvent("EVT-1", new OperationId("OPE-1"), "SOLICITADA",
                        "Operation requested with protocol PRT-1",
                        Instant.parse("2026-11-22T12:34:56Z")))));

        mockMvc.perform(get("/api/portability/{operationId}/audit", "OPE-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].eventId").value("EVT-1"))
                .andExpect(jsonPath("$[0].operationId").value("OPE-1"))
                .andExpect(jsonPath("$[0].eventType").value("SOLICITADA"))
                .andExpect(jsonPath("$[0].message").value("Operation requested with protocol PRT-1"))
                .andExpect(jsonPath("$[0].occurredAt").value("2026-11-22T12:34:56Z"));
    }

    @Test
    void shouldRespond200WithEmptyListWhenOperationHasNoEvents() throws Exception {
        when(getOperationAuditUseCase.getAuditTrail(new OperationId("OPE-1"))).thenReturn(Optional.of(List.of()));

        mockMvc.perform(get("/api/portability/{operationId}/audit", "OPE-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void shouldRespond404WhenOperationDoesNotExist() throws Exception {
        when(getOperationAuditUseCase.getAuditTrail(new OperationId("missing"))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/portability/{operationId}/audit", "missing"))
                .andExpect(status().isNotFound());
    }
}
