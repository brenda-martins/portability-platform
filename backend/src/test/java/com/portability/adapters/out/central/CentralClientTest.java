package com.portability.adapters.out.central;

import com.portability.domain.model.DiscountInstallment;
import com.portability.domain.model.InstallmentStatus;
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
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void mustSendPayloadSignedEUuseIdReturnedByCentral() {
        String authorizationId = client.registerAuthorization("BEN-123", "AGR-001");

        assertEquals("AUT-1", authorizationId);
        assertEquals("{\"beneficiarioId\":\"BEN-123\",\"convenioId\":\"AGR-001\"}", receivedBody);
        assertEquals(signatureService.sign(receivedBody), receivedSignature);
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

    private static final String CANONICAL_PORTABILITY_BODY =
            "{\"beneficiarioId\":\"BEN-123\",\"parcelas\":[\"INS-1\"],"
                    + "\"credorCessionario\":\"Nova Credora S.A.\","
                    + "\"contaRecebimento\":{\"banco\":\"001\",\"agencia\":\"1234\",\"conta\":\"56789-0\"}}";
}
