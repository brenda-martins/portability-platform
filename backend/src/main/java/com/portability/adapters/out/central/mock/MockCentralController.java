package com.portability.adapters.out.central.mock;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.portability.domain.ports.out.SignaturePort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local simulation of the Central de Convênios, following the contract suggested in the PDF.
 */
@RestController
@RequestMapping("/central")
public class MockCentralController {

    private final SignaturePort signaturePort;
    private final ObjectMapper objectMapper;
    private final Set<String> registeredProtocols = ConcurrentHashMap.newKeySet();
    private final Map<String, AtomicInteger> statusConsultations = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> updatedAccounts = new ConcurrentHashMap<>();

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
    public ResponseEntity<List<Map<String, Object>>> getAgenda(@PathVariable String beneficiaryId) {
        return ResponseEntity.ok(agendaFor(beneficiaryId));
    }

    private static List<Map<String, Object>> agendaFor(String beneficiaryId) {
        if (!"BEN-123".equals(beneficiaryId)) {
            return List.of();
        }
        return List.of(
                installment("INS-1", "AGR-001", "120.50", "2099-01-10", "ABERTA"),
                installment("INS-2", "AGR-001", "80.00", "2020-01-10", "ABERTA"),
                installment("INS-3", "AGR-001", "60.00", "2099-02-10", "PORTADA"));
    }

    private static Map<String, Object> installment(
            String parcelaId, String convenioId, String valor, String dataDesconto, String status) {
        return Map.of(
                "parcelaId", parcelaId,
                "convenioId", convenioId,
                "valor", new BigDecimal(valor),
                "dataDesconto", dataDesconto,
                "status", status);
    }

    @PostMapping("/operacoes-portabilidade")
    public ResponseEntity<?> registerPortability(
            @RequestBody String body,
            @RequestHeader(value = "X-Signature", required = false) String signature
    ) {
        JsonNode payload;
        String canonicalBody;
        try {
            payload = objectMapper.readTree(body);
            if (payload == null || !payload.isObject()) {
                return ResponseEntity.badRequest().build();
            }
            ObjectNode canonical = (ObjectNode) payload.deepCopy();
            canonical.remove("assinatura");
            canonicalBody = objectMapper.writeValueAsString(canonical);
        } catch (JacksonException exception) {
            return ResponseEntity.badRequest().build();
        }

        if (signature == null || !signaturePort.verify(canonicalBody, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!isValidPortabilityPayload(payload)) {
            return ResponseEntity.badRequest().build();
        }

        String protocol = "PRT-" + UUID.nameUUIDFromBytes(canonicalBody.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        registeredProtocols.add(protocol);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("protocolo", protocol));
    }

    private static boolean isValidPortabilityPayload(JsonNode payload) {
        if (isBlank(payload.path("beneficiarioId").asText(""))
                || isBlank(payload.path("assinatura").asText(""))
                || isBlank(payload.path("credorCessionario").asText(""))) {
            return false;
        }
        JsonNode installments = payload.path("parcelas");
        if (!installments.isArray() || installments.isEmpty()) {
            return false;
        }
        for (JsonNode installment : installments) {
            if (isBlank(installment.asText(""))) {
                return false;
            }
        }
        JsonNode account = payload.path("contaRecebimento");
        return account.isObject()
                && !isBlank(account.path("banco").asText(""))
                && !isBlank(account.path("agencia").asText(""))
                && !isBlank(account.path("conta").asText(""));
    }

    @GetMapping("/operacoes-portabilidade/{protocol}")
    public ResponseEntity<?> getStatus(@PathVariable String protocol) {
        if (isBlank(protocol) || !registeredProtocols.contains(protocol)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        int consultations = statusConsultations.computeIfAbsent(protocol, key -> new AtomicInteger())
                .incrementAndGet();
        if (consultations <= 2) {
            return ResponseEntity.noContent().header(HttpHeaders.RETRY_AFTER, "1").build();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "CONFIRMADA");
        body.put("motivoRejeicao", null);
        return ResponseEntity.ok(body);
    }

    @PutMapping("/operacoes-portabilidade/{protocol}/conta-recebimento")
    public ResponseEntity<?> updateReceivingAccount(
            @PathVariable String protocol,
            @RequestBody String body,
            @RequestHeader(value = "X-Signature", required = false) String signature
    ) {
        JsonNode payload;
        String canonicalBody;
        try {
            payload = objectMapper.readTree(body);
            if (payload == null || !payload.isObject()) {
                return ResponseEntity.badRequest().build();
            }
            ObjectNode canonical = (ObjectNode) payload.deepCopy();
            canonical.remove("assinatura");
            canonicalBody = objectMapper.writeValueAsString(canonical);
        } catch (JacksonException exception) {
            return ResponseEntity.badRequest().build();
        }

        if (signature == null || !signaturePort.verify(canonicalBody, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (isBlank(protocol) || !registeredProtocols.contains(protocol)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!isValidAccountUpdatePayload(payload, protocol)) {
            return ResponseEntity.badRequest().build();
        }

        Map<String, String> account = new LinkedHashMap<>();
        account.put("banco", payload.path("banco").asText());
        account.put("agencia", payload.path("agencia").asText());
        account.put("conta", payload.path("conta").asText());
        updatedAccounts.put(protocol, account);
        return ResponseEntity.ok(account);
    }

    private static boolean isValidAccountUpdatePayload(JsonNode payload, String protocol) {
        return protocol.equals(payload.path("protocolo").asText(""))
                && !isBlank(payload.path("banco").asText(""))
                && !isBlank(payload.path("agencia").asText(""))
                && !isBlank(payload.path("conta").asText(""));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
