package com.portability.adapters.out.central;

import com.portability.domain.model.CentralOperationStatus;
import com.portability.domain.model.DiscountInstallment;
import com.portability.domain.model.InstallmentStatus;
import com.portability.domain.model.OperationStatus;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.ports.out.CentralUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CentralClientTest {

    private static final String SECRET = "segredo-de-teste";

    private HttpServer server;
    private HmacSignatureService signatureService;
    private CentralClient client;
    private String receivedBody;
    private String receivedSignature;
    private int responseStatus;
    private String responseBody;
    private int agendaStatus;
    private String agendaBody;
    private int operationStatus;
    private String operationBody;
    private int statusCode;
    private String statusBody;
    private String statusRetryAfter;
    private String statusPath;
    private String statusSignature;
    private int accountStatus;
    private String accountBody;
    private String accountPath;
    private String accountSignature;

    @BeforeEach
    void setUp() throws IOException {
        signatureService = new HmacSignatureService(SECRET);
        responseStatus = 201;
        responseBody = "{\"autorizacaoId\":\"AUT-1\"}";
        agendaStatus = 200;
        agendaBody = "[{\"parcelaId\":\"INS-1\",\"convenioId\":\"AGR-001\",\"valor\":120.50,"
                + "\"dataDesconto\":\"2099-01-10\",\"status\":\"ABERTA\"}]";
        operationStatus = 202;
        operationBody = "{\"protocolo\":\"PRT-1\"}";
        statusCode = 204;
        statusBody = "";
        statusRetryAfter = "1";
        accountStatus = 200;
        accountBody = "{\"banco\":\"002\",\"agencia\":\"4321\",\"conta\":\"98765-4\"}";
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/central/autorizacoes", exchange -> {
            receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            receivedSignature = exchange.getRequestHeaders().getFirst("X-Signature");
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/central/beneficiarios/", exchange -> {
            byte[] response = agendaBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(agendaStatus, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/central/operacoes-portabilidade", exchange -> {
            if ("PUT".equals(exchange.getRequestMethod())) {
                accountPath = exchange.getRequestURI().getPath();
                accountSignature = exchange.getRequestHeaders().getFirst("X-Signature");
                receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                byte[] response = accountBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(accountStatus, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
                return;
            }
            if ("GET".equals(exchange.getRequestMethod())) {
                statusPath = exchange.getRequestURI().getPath();
                statusSignature = exchange.getRequestHeaders().getFirst("X-Signature");
                exchange.getResponseHeaders().add("Retry-After", statusRetryAfter);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                if (statusCode == 204) {
                    exchange.sendResponseHeaders(204, -1);
                } else {
                    byte[] response = statusBody.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(statusCode, response.length);
                    exchange.getResponseBody().write(response);
                }
                exchange.close();
                return;
            }
            receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            receivedSignature = exchange.getRequestHeaders().getFirst("X-Signature");
            byte[] response = operationBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(operationStatus, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        client = new CentralClient(
                "http://localhost:" + server.getAddress().getPort() + "/central",
                signatureService,
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void mustSendSignedPayloadAndUseIdReturnedByCentral() throws Exception {
        String authorizationId = client.registerAuthorization("BEN-123", "AGR-001");

        assertEquals("AUT-1", authorizationId);
        JsonNode payload = new ObjectMapper().readTree(receivedBody);
        assertEquals("BEN-123", payload.path("beneficiarioId").asText());
        assertEquals("AGR-001", payload.path("convenioId").asText());
        String canonical = "{\"beneficiarioId\":\"BEN-123\",\"convenioId\":\"AGR-001\"}";
        String expectedSignature = signatureService.sign(canonical);
        assertEquals(expectedSignature, payload.path("assinatura").asText());
        assertEquals(expectedSignature, receivedSignature);
    }

    @Test
    void shouldFailWhenCentralRespondsWithError() {
        responseStatus = 500;
        responseBody = "{\"erro\":\"indisponivel\"}";

        assertThrows(CentralUnavailableException.class,
                () -> client.registerAuthorization("BEN-123", "AGR-001"));
    }

    @Test
    void shouldTranslateAgendaFromCentralContract() {
        List<DiscountInstallment> agenda = client.fetchAgenda("BEN-123");

        assertEquals(1, agenda.size());
        DiscountInstallment installment = agenda.get(0);
        assertEquals("INS-1", installment.installmentId());
        assertEquals("AGR-001", installment.agreementId());
        assertEquals(new BigDecimal("120.50"), installment.amount());
        assertEquals(LocalDate.parse("2099-01-10"), installment.discountDate());
        assertEquals(InstallmentStatus.OPEN, installment.status());
    }

    @Test
    void shouldFailWhenAgendaRespondsWithError() {
        agendaStatus = 500;

        assertThrows(CentralUnavailableException.class, () -> client.fetchAgenda("BEN-123"));
    }

    @Test
    void shouldFailWhenAgendaHasUnknownStatus() {
        agendaBody = "[{\"parcelaId\":\"INS-1\",\"convenioId\":\"AGR-001\",\"valor\":120.50,"
                + "\"dataDesconto\":\"2099-01-10\",\"status\":\"DESCONHECIDO\"}]";

        assertThrows(CentralUnavailableException.class, () -> client.fetchAgenda("BEN-123"));
    }

    @Test
    void mustSendCanonicalBodySignedAndReturnProtocol() throws Exception {
        String protocol = client.registerPortability("BEN-123", List.of("INS-1"),
                "Nova Credora S.A.", new ReceivingAccount("001", "1234", "56789-0"));

        assertEquals("PRT-1", protocol);
        JsonNode payload = new ObjectMapper().readTree(receivedBody);
        assertEquals("BEN-123", payload.path("beneficiarioId").asText());
        assertEquals("INS-1", payload.path("parcelas").get(0).asText());
        assertEquals("Nova Credora S.A.", payload.path("credorCessionario").asText());
        assertEquals("001", payload.path("contaRecebimento").path("banco").asText());
        String expectedSignature = signatureService.sign(CANONICAL_PORTABILITY_BODY);
        assertEquals(expectedSignature, payload.path("assinatura").asText());
        assertEquals(expectedSignature, receivedSignature);
    }

    @Test
    void shouldFailWhenCentralRejectsPortability() {
        operationStatus = 500;

        assertThrows(CentralUnavailableException.class, () -> client.registerPortability(
                "BEN-123", List.of("INS-1"), "Nova Credora S.A.",
                new ReceivingAccount("001", "1234", "56789-0")));
    }

    @Test
    void shouldFailWhenCentralReturnsBlankProtocol() {
        operationBody = "{\"protocolo\":\" \"}";

        assertThrows(CentralUnavailableException.class, () -> client.registerPortability(
                "BEN-123", List.of("INS-1"), "Nova Credora S.A.",
                new ReceivingAccount("001", "1234", "56789-0")));
    }

    @Test
    void mustTranslatePendingStatusAndRetryAfterWithoutSendingSignature() {
        statusCode = 204;
        statusRetryAfter = "2";

        CentralOperationStatus status = client.consultStatus("PRT-1");

        assertEquals(OperationStatus.PENDING, status.status());
        assertEquals(Duration.ofSeconds(2), status.retryAfter());
        assertEquals("/central/operacoes-portabilidade/PRT-1", statusPath);
        assertNull(statusSignature);
    }

    @Test
    void mustTranslateConfirmedStatusFromCentral() {
        statusCode = 200;
        statusBody = "{\"status\":\"CONFIRMADA\",\"motivoRejeicao\":null}";

        CentralOperationStatus status = client.consultStatus("PRT-1");

        assertEquals(OperationStatus.CONFIRMED, status.status());
        assertEquals(Duration.ZERO, status.retryAfter());
    }

    @Test
    void mustTranslateRejectionReasonFromCentral() {
        statusCode = 200;
        statusBody = "{\"status\":\"REJEITADA\",\"motivoRejeicao\":\"Convênio bloqueado\"}";

        CentralOperationStatus status = client.consultStatus("PRT-1");

        assertEquals(OperationStatus.REJECTED, status.status());
        assertEquals("Convênio bloqueado", status.rejectionReason());
    }

    @Test
    void shouldFailWhenCentralStatusRespondsWithError() {
        statusCode = 500;

        assertThrows(CentralUnavailableException.class, () -> client.consultStatus("PRT-1"));
    }

    @Test
    void shouldTranslateServiceUnavailableDuringStatusConsultationIntoCentralUnavailableException() {
        statusCode = 503;
        statusBody = "";

        assertThrows(CentralUnavailableException.class, () -> client.consultStatus("PRT-1"));
    }

    @Test
    void shouldFailWhenCentralStatusIsUnknown() {
        statusCode = 200;
        statusBody = "{\"status\":\"DESCONHECIDO\",\"motivoRejeicao\":null}";

        assertThrows(CentralUnavailableException.class, () -> client.consultStatus("PRT-1"));
    }

    @Test
    void shouldFailWhenRetryAfterIsInvalid() {
        statusCode = 204;
        statusRetryAfter = "inválido";

        assertThrows(CentralUnavailableException.class, () -> client.consultStatus("PRT-1"));
    }

    @Test
    void mustSendSignedPutWithProtocolAndReturnUpdatedAccount() throws Exception {
        ReceivingAccount updated = client.updateReceivingAccount(
                "PRT-1", new ReceivingAccount("002", "4321", "98765-4"));

        assertEquals(new ReceivingAccount("002", "4321", "98765-4"), updated);
        assertEquals("/central/operacoes-portabilidade/PRT-1/conta-recebimento", accountPath);
        JsonNode payload = new ObjectMapper().readTree(receivedBody);
        assertEquals("PRT-1", payload.path("protocolo").asText());
        assertEquals("002", payload.path("banco").asText());
        assertEquals("4321", payload.path("agencia").asText());
        assertEquals("98765-4", payload.path("conta").asText());
        String expectedSignature = signatureService.sign(CANONICAL_ACCOUNT_BODY);
        assertEquals(expectedSignature, payload.path("assinatura").asText());
        assertEquals(expectedSignature, accountSignature);
    }

    @Test
    void shouldFailWhenCentralRejectsAccountUpdate() {
        accountStatus = 401;

        assertThrows(CentralUnavailableException.class, () -> client.updateReceivingAccount(
                "PRT-1", new ReceivingAccount("002", "4321", "98765-4")));
    }

    @Test
    void shouldFailWhenCentralReturnsUnexpectedAccount() {
        accountBody = "{\"banco\":\" \",\"agencia\":\"4321\",\"conta\":\"98765-4\"}";

        assertThrows(CentralUnavailableException.class, () -> client.updateReceivingAccount(
                "PRT-1", new ReceivingAccount("002", "4321", "98765-4")));
    }

    private static final String CANONICAL_PORTABILITY_BODY =
            "{\"beneficiarioId\":\"BEN-123\",\"parcelas\":[\"INS-1\"],"
                    + "\"credorCessionario\":\"Nova Credora S.A.\","
                    + "\"contaRecebimento\":{\"banco\":\"001\",\"agencia\":\"1234\",\"conta\":\"56789-0\"}}";

    private static final String CANONICAL_ACCOUNT_BODY =
            "{\"protocolo\":\"PRT-1\",\"banco\":\"002\",\"agencia\":\"4321\",\"conta\":\"98765-4\"}";
}
