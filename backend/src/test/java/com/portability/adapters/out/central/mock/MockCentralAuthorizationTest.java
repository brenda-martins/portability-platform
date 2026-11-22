package com.portability.adapters.out.central.mock;

import com.portability.adapters.out.central.HmacSignatureService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MockCentralAuthorizationTest {

    private static final String VALID_BODY =
            "{\"beneficiarioId\":\"BEN-123\",\"convenioId\":\"AGR-001\"}";

    private HmacSignatureService signatureService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        signatureService = new HmacSignatureService("segredo-de-teste");
        mockMvc = MockMvcBuilders.standaloneSetup(
                new MockCentralController(signatureService, new ObjectMapper())).build();
    }

    @Test
    void shouldRegisterAuthorizationWhenSignatureIsValid() throws Exception {
        mockMvc.perform(post("/central/autorizacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", signatureService.sign(VALID_BODY))
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autorizacaoId").value(startsWith("AUT-")));
    }

    @Test
    void shouldRejectAuthorizationWhenSignatureIsInvalid() throws Exception {
        mockMvc.perform(post("/central/autorizacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", "assinatura-invalida")
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectAuthorizationWhenPayloadIsInvalid() throws Exception {
        String body = "{\"beneficiarioId\":\" \",\"convenioId\":\"AGR-001\"}";

        mockMvc.perform(post("/central/autorizacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", signatureService.sign(body))
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
