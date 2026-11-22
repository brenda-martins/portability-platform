package com.portability.adapters.out.central.mock;

import com.portability.adapters.out.central.HmacSignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MockCentralAgendaTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new MockCentralController(new HmacSignatureService("segredo-de-teste"), new ObjectMapper()))
                .build();
    }

    @Test
    void shouldReturnAgendaFixtureForKnownBeneficiary() throws Exception {
        mockMvc.perform(get("/central/beneficiarios/BEN-123/agenda"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].parcelaId").value("INS-1"))
                .andExpect(jsonPath("$[0].convenioId").value("AGR-001"))
                .andExpect(jsonPath("$[0].status").value("ABERTA"))
                .andExpect(jsonPath("$[1].parcelaId").value("INS-2"))
                .andExpect(jsonPath("$[2].status").value("PORTADA"));
    }

    @Test
    void shouldReturnEmptyAgendaForUnknownBeneficiary() throws Exception {
        mockMvc.perform(get("/central/beneficiarios/BEN-999/agenda"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
