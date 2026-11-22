package com.portability.adapters.out.central;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HmacSignatureServiceTest {

    private static final String SECRET = "segredo-de-teste";
    private static final String PAYLOAD = "{\"beneficiarioId\":\"BEN-123\",\"convenioId\":\"AGR-001\"}";

    private final HmacSignatureService service = new HmacSignatureService(SECRET);

    @Test
    void shouldSignPayloadWithHmacSha256InBase64() throws Exception {
        assertEquals(expectedSignature(PAYLOAD), service.sign(PAYLOAD));
    }

    @Test
    void shouldVerifyValidSignature() {
        assertTrue(service.verify(PAYLOAD, service.sign(PAYLOAD)));
    }

    @Test
    void shouldRejectDivergentSignature() {
        assertFalse(service.verify(PAYLOAD, "assinatura-invalida"));
    }

    private static String expectedSignature(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
