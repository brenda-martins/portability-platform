package com.portability.adapters.out.central.mock;

import com.portability.adapters.out.central.HmacSignatureService;
import com.portability.domain.model.ReceivingAccount;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockCentralReceivingAccountTest {

    private final HmacSignatureService signatureService = new HmacSignatureService("segredo-de-teste");
    private final MockCentralController controller =
            new MockCentralController(signatureService, new ObjectMapper());

    @Test
    void shouldAcceptSignedAccountUpdateWhenProtocolIsRegistered() {
        String protocol = registerPortability("BEN-F05-ACCEPT");
        String canonical = canonicalAccount(protocol, "002", "4321", "98765-4");
        String signature = signatureService.sign(canonical);

        ResponseEntity<?> response = controller.updateReceivingAccount(
                protocol, withSignature(canonical, signature), signature);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("002", body.get("banco"));
        assertEquals("4321", body.get("agencia"));
        assertEquals("98765-4", body.get("conta"));

        Optional<ReceivingAccount> stored = controller.accountFor(protocol);
        assertTrue(stored.isPresent());
        assertEquals(new ReceivingAccount("002", "4321", "98765-4"), stored.get());
    }

    @Test
    void shouldRespond404WhenProtocolIsUnknown() {
        String protocol = "PRT-F05-UNKNOWN";
        String canonical = canonicalAccount(protocol, "002", "4321", "98765-4");
        String signature = signatureService.sign(canonical);

        ResponseEntity<?> response = controller.updateReceivingAccount(
                protocol, withSignature(canonical, signature), signature);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
        assertTrue(controller.accountFor(protocol).isEmpty());
    }

    @Test
    void shouldRespond401WhenSignatureIsInvalid() {
        String protocol = registerPortability("BEN-F05-SIGNATURE");
        String canonical = canonicalAccount(protocol, "002", "4321", "98765-4");

        ResponseEntity<?> response = controller.updateReceivingAccount(
                protocol, withSignature(canonical, "assinatura-invalida"), "assinatura-invalida");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertTrue(controller.accountFor(protocol).isEmpty());
    }

    @Test
    void shouldRespond400WhenAccountIsIncomplete() {
        String protocol = registerPortability("BEN-F05-INVALID");
        String canonical = canonicalAccount(protocol, "002", " ", "98765-4");
        String signature = signatureService.sign(canonical);

        ResponseEntity<?> response = controller.updateReceivingAccount(
                protocol, withSignature(canonical, signature), signature);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(controller.accountFor(protocol).isEmpty());
    }

    private String registerPortability(String beneficiaryId) {
        String canonical = "{\"beneficiarioId\":\"" + beneficiaryId + "\",\"parcelas\":[\"INS-1\"],"
                + "\"credorCessionario\":\"Nova Credora S.A.\","
                + "\"contaRecebimento\":{\"banco\":\"001\",\"agencia\":\"1234\",\"conta\":\"56789-0\"}}";
        String signature = signatureService.sign(canonical);
        ResponseEntity<?> response = controller.registerPortability(withSignature(canonical, signature), signature);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) response.getBody();
        return (String) payload.get("protocolo");
    }

    private static String canonicalAccount(String protocol, String bank, String agency, String account) {
        return "{\"protocolo\":\"" + protocol + "\",\"banco\":\"" + bank + "\",\"agencia\":\""
                + agency + "\",\"conta\":\"" + account + "\"}";
    }

    private static String withSignature(String canonical, String signature) {
        return canonical.substring(0, canonical.length() - 1) + ",\"assinatura\":\"" + signature + "\"}";
    }
}
