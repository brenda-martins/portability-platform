package com.portability.adapters.out.central.mock;

import com.portability.adapters.out.central.HmacSignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MockCentralPortabilityTest {

    private static final String SECRET = "segredo-de-teste";

    private MockMvc mockMvc;
    private HmacSignatureService signer;

    @BeforeEach
    void setUp() {
        signer = new HmacSignatureService(SECRET);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new MockCentralController(signer, new ObjectMapper()))
                .build();
    }

    @Test
    void shouldRespond202WithProtocolWhenSignatureAndPayloadAreValid() throws Exception {
        String canonical = canonical("BEN-123", "INS-1", "Nova Credora S.A.");

        mockMvc.perform(request(signed(canonical), signer.sign(canonical)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.protocolo", startsWith("PRT-")));
    }

    @Test
    void shouldReturnSameProtocolForSamePayload() throws Exception {
        String canonical = canonical("BEN-123", "INS-1", "Nova Credora S.A.");
        String body = signed(canonical);

        String first = register(body, signer.sign(canonical));
        String second = register(body, signer.sign(canonical));

        assertEquals(first, second);
    }

    @Test
    void shouldRespond401WhenSignatureIsMissing() throws Exception {
        String canonical = canonical("BEN-123", "INS-1", "Nova Credora S.A.");

        mockMvc.perform(request(signed(canonical), null))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRespond401WhenSignatureIsInvalid() throws Exception {
        String canonical = canonical("BEN-123", "INS-1", "Nova Credora S.A.");

        mockMvc.perform(request(signed(canonical), "assinatura-invalida"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRespond400WhenPayloadIsInvalid() throws Exception {
        String canonical = canonical(" ", "INS-1", "Nova Credora S.A.");

        mockMvc.perform(request(signed(canonical), signer.sign(canonical)))
                .andExpect(status().isBadRequest());
    }

    private String register(String body, String signature) throws Exception {
        return mockMvc.perform(request(body, signature))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
            String body, String signature) {
        var request = post("/central/operacoes-portabilidade")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        return signature == null ? request : request.header("X-Signature", signature);
    }

    private String signed(String canonical) {
        return canonical.substring(0, canonical.length() - 1)
                + ",\"assinatura\":\"" + signer.sign(canonical) + "\"}";
    }

    private static String canonical(String beneficiaryId, String installmentId, String creditor) {
        return "{\"beneficiarioId\":\"" + beneficiaryId + "\",\"parcelas\":[\"" + installmentId + "\"],"
                + "\"credorCessionario\":\"" + creditor + "\","
                + "\"contaRecebimento\":{\"banco\":\"001\",\"agencia\":\"1234\",\"conta\":\"56789-0\"}}";
    }
}
