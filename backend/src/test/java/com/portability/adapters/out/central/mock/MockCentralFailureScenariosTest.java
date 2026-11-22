package com.portability.adapters.out.central.mock;

import com.portability.adapters.out.central.HmacSignatureService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MockCentralFailureScenariosTest {

    private final HmacSignatureService signatureService = new HmacSignatureService("segredo-de-teste");
    private final MockCentralController controller =
            new MockCentralController(signatureService, new ObjectMapper());

    @Test
    void shouldRejectWhenCreditorContainsRejectionMarker() {
        String protocol = registerPortability("BEN-F07-REJECT", "Nova Credora REJEITAR S.A.");

        assertEquals(HttpStatus.NO_CONTENT, controller.getStatus(protocol).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, controller.getStatus(protocol).getStatusCode());
        ResponseEntity<?> response = controller.getStatus(protocol);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("REJEITADA", body.get("status"));
        assertEquals("Convênio de origem não autoriza a portabilidade", body.get("motivoRejeicao"));
    }

    @Test
    void shouldRespondServiceUnavailableWhenCreditorContainsFailureMarker() {
        String protocol = registerPortability("BEN-F07-FAIL", "Nova Credora FALHA S.A.");

        ResponseEntity<?> first = controller.getStatus(protocol);
        ResponseEntity<?> second = controller.getStatus(protocol);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, first.getStatusCode());
        assertNull(first.getBody());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, second.getStatusCode());
        assertNull(second.getBody());
    }

    @Test
    void shouldMatchMarkersIgnoringCase() {
        String protocol = registerPortability("BEN-F07-LOWER", "nova credora rejeitar s.a.");
        controller.getStatus(protocol);
        controller.getStatus(protocol);

        ResponseEntity<?> response = controller.getStatus(protocol);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("REJEITADA", body.get("status"));
    }

    private String registerPortability(String beneficiaryId, String creditor) {
        String canonical = "{\"beneficiarioId\":\"" + beneficiaryId + "\",\"parcelas\":[\"INS-1\"],"
                + "\"credorCessionario\":\"" + creditor + "\","
                + "\"contaRecebimento\":{\"banco\":\"001\",\"agencia\":\"1234\",\"conta\":\"56789-0\"}}";
        String signature = signatureService.sign(canonical);
        ResponseEntity<?> response = controller.registerPortability(withSignature(canonical, signature), signature);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) response.getBody();
        return (String) payload.get("protocolo");
    }

    private static String withSignature(String canonical, String signature) {
        return canonical.substring(0, canonical.length() - 1) + ",\"assinatura\":\"" + signature + "\"}";
    }
}
