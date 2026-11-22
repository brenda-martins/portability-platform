package com.portability.adapters.out.central.mock;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.portability.domain.ports.out.SignaturePort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Local simulation of the Central de Convênios, following the contract suggested in the PDF.
 */
@RestController
@RequestMapping("/central")
public class MockCentralController {

    private final SignaturePort signaturePort;
    private final ObjectMapper objectMapper;

    public MockCentralController(SignaturePort signaturePort, ObjectMapper objectMapper) {
        this.signaturePort = signaturePort;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/autorizacoes")
    public ResponseEntity<?> registerAuthorization(
            @RequestBody String body,
            @RequestHeader(value = "X-Signature", required = false) String signature
    ) {
        if (signature == null || !signaturePort.verify(body, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(body);
        } catch (JacksonException exception) {
            return ResponseEntity.badRequest().build();
        }
        if (payload == null || !payload.isObject()
                || isBlank(payload.path("beneficiarioId").asText(""))
                || isBlank(payload.path("convenioId").asText(""))) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("autorizacaoId", "AUT-" + UUID.randomUUID()));
    }

    @GetMapping("/beneficiarios/{beneficiaryId}/agenda")
    public ResponseEntity<Void> getAgenda(@PathVariable String beneficiaryId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping("/operacoes-portabilidade")
    public ResponseEntity<Void> registerPortability(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping("/operacoes-portabilidade/{protocol}")
    public ResponseEntity<Void> getStatus(@PathVariable String protocol) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
