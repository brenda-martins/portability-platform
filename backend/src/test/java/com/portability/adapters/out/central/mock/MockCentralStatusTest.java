package com.portability.adapters.out.central.mock;

import com.portability.adapters.out.central.HmacSignatureService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MockCentralStatusTest {

    private final HmacSignatureService signatureService = new HmacSignatureService("segredo-de-teste");
    private final MockCentralController controller =
            new MockCentralController(signatureService, new ObjectMapper());

    @Test
    void shouldKeepPendingWithRetryAfterWhenConsultedTheFirstTwoTimes() {
        String protocol = registerPortability("BEN-F04-PENDING");

        ResponseEntity<?> first = controller.getStatus(protocol);
        ResponseEntity<?> second = controller.getStatus(protocol);

        assertEquals(HttpStatus.NO_CONTENT, first.getStatusCode());
        assertEquals("1", first.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals(HttpStatus.NO_CONTENT, second.getStatusCode());
        assertEquals("1", second.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void shouldConfirmWhenConsultedFromTheThirdTimeOnward() {
        String protocol = registerPortability("BEN-F04-CONFIRMED");
        controller.getStatus(protocol);
        controller.getStatus(protocol);

        ResponseEntity<?> response = controller.getStatus(protocol);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("CONFIRMADA", body.get("status"));
        assertNull(body.get("motivoRejeicao"));
    }

    @Test
    void shouldRespond404WhenProtocolIsUnknown() {
        ResponseEntity<?> response = controller.getStatus("PRT-F04-UNKNOWN");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    private String registerPortability(String beneficiaryId) {
        String canonical = "{\"beneficiarioId\":\"" + beneficiaryId + "\",\"parcelas\":[\"INS-1\"],"
                + "\"credorCessionario\":\"Nova Credora S.A.\","
                + "\"contaRecebimento\":{\"banco\":\"001\",\"agencia\":\"1234\",\"conta\":\"56789-0\"}}";
        String signature = signatureService.sign(canonical);
        String body = canonical.substring(0, canonical.length() - 1) + ",\"assinatura\":\"" + signature + "\"}";
        ResponseEntity<?> response = controller.registerPortability(body, signature);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) response.getBody();
        return (String) payload.get("protocolo");
    }
}
