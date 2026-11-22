package com.portability.adapters.in.web;

import com.portability.adapters.out.persistence.SpringDataOperationRepository;
import com.portability.domain.model.OperationStatus;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.ports.out.OperationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reproduces the real HTTP read path, where no OpenEntityManagerInView session exists. */
@SpringBootTest
@ActiveProfiles("test")
class GetOperationStatusIntegrationTest {

    private static final String OPERATION_ID = "OPE-F04-LAZY";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private OperationRepository repository;

    @Autowired
    private SpringDataOperationRepository springRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @AfterEach
    void cleanUp() {
        springRepository.deleteById(OPERATION_ID);
    }

    @Test
    void shouldReturnOperationWhenReadOutsideTransaction() throws Exception {
        repository.save(new PortabilityOperation(
                OPERATION_ID, "BEN-123", List.of("INS-1", "INS-2"),
                "Nova Credora S.A.", new ReceivingAccount("001", "1234", "56789-0"),
                OperationStatus.CONFIRMED, "PRT-1", null,
                Instant.parse("2026-11-22T12:34:56Z")), "key-lazy-1");

        mockMvc.perform(get("/api/portability/{operationId}", OPERATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value(OPERATION_ID))
                .andExpect(jsonPath("$.installmentIds[0]").value("INS-1"))
                .andExpect(jsonPath("$.installmentIds[1]").value("INS-2"));
    }
}
