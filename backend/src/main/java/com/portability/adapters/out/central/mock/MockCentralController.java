package com.portability.adapters.out.central.mock;

import org.springframework.beans.factory.annotation.Autowired;

import com.portability.adapters.out.central.CentralRequestCodec;
import com.portability.adapters.out.central.HmacSignatureService;
import com.portability.domain.model.ReceivingAccount;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Local simulation of the Central de Convênios, following the contract suggested in the PDF. */
@RestController
@RequestMapping("/central")
public class MockCentralController {

    private final CentralRequestCodec codec;
    private final ObjectMapper objectMapper;
    private final Set<String> registeredProtocols = ConcurrentHashMap.newKeySet();
    private final Map<String, AtomicInteger> statusConsultations = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> updatedAccounts = new ConcurrentHashMap<>();
    private final Map<String, CreditorMarker> creditorMarkers = new ConcurrentHashMap<>();

    private enum CreditorMarker {
        CONFIRM, REJECT, FAIL
    }

    @Autowired
    public MockCentralController(CentralRequestCodec codec, ObjectMapper objectMapper) {
        this.codec = codec;
        this.objectMapper = objectMapper;
    }

    public MockCentralController(HmacSignatureService signatureService, ObjectMapper objectMapper) {
        this(new CentralRequestCodec(signatureService, objectMapper), objectMapper);
    }

    @PostMapping("/autorizacoes")
    public ResponseEntity<?> registerAuthorization(
            @RequestBody String body,
            @RequestHeader(value = "X-Signature", required = false) String signature
    ) {
        if (!codec.isValidSignature(body, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        JsonNode payload = parse(body);
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
        JsonNode payload = parse(body);
        if (payload == null || !payload.isObject()) {
            return ResponseEntity.badRequest().build();
        }
        if (!codec.isValidSignature(body, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!isValidPortabilityPayload(payload)) {
            return ResponseEntity.badRequest().build();
        }

        String canonicalBody = codec.canonicalBody(body).orElseThrow();
        String protocol = "PRT-" + UUID.nameUUIDFromBytes(canonicalBody.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString().replace("-", "");
        registeredProtocols.add(protocol);
        creditorMarkers.put(protocol, markerFor(payload.path("credorCessionario").asText("")));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("protocolo", protocol));
    }

    private static CreditorMarker markerFor(String creditorAssignee) {
        String value = creditorAssignee == null ? "" : creditorAssignee.toUpperCase(Locale.ROOT);
        if (value.contains("REJEITAR")) {
            return CreditorMarker.REJECT;
        }
        if (value.contains("FALHA")) {
            return CreditorMarker.FAIL;
        }
        return CreditorMarker.CONFIRM;
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
        CreditorMarker marker = creditorMarkers.getOrDefault(protocol, CreditorMarker.CONFIRM);
        if (marker == CreditorMarker.FAIL) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        int consultations = statusConsultations.computeIfAbsent(protocol, key -> new AtomicInteger())
                .incrementAndGet();
        if (consultations <= 2) {
            return ResponseEntity.noContent().header(HttpHeaders.RETRY_AFTER, "1").build();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", marker == CreditorMarker.REJECT ? "REJEITADA" : "CONFIRMADA");
        body.put("motivoRejeicao", marker == CreditorMarker.REJECT
                ? "Convênio de origem não autoriza a portabilidade" : null);
        return ResponseEntity.ok(body);
    }

    @PutMapping("/operacoes-portabilidade/{protocol}/conta-recebimento")
    public ResponseEntity<?> updateReceivingAccount(
            @PathVariable String protocol,
            @RequestBody String body,
            @RequestHeader(value = "X-Signature", required = false) String signature
    ) {
        JsonNode payload = parse(body);
        if (payload == null || !payload.isObject()) {
            return ResponseEntity.badRequest().build();
        }
        if (!codec.isValidSignature(body, signature)) {
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

    /** Package-private inspection point used by the mock's own test suite. */
    Optional<ReceivingAccount> accountFor(String protocol) {
        Map<String, String> account = updatedAccounts.get(protocol);
        if (account == null) {
            return Optional.empty();
        }
        return Optional.of(new ReceivingAccount(
                account.get("banco"), account.get("agencia"), account.get("conta")));
    }

    private static boolean isValidAccountUpdatePayload(JsonNode payload, String protocol) {
        return protocol.equals(payload.path("protocolo").asText(""))
                && !isBlank(payload.path("banco").asText(""))
                && !isBlank(payload.path("agencia").asText(""))
                && !isBlank(payload.path("conta").asText(""));
    }

    private JsonNode parse(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JacksonException exception) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
