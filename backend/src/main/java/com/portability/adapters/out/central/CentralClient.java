package com.portability.adapters.out.central;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.portability.domain.model.CentralOperationStatus;
import com.portability.domain.model.DiscountInstallment;
import com.portability.domain.model.InstallmentStatus;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.ports.out.CentralAuthorizationPort;
import com.portability.domain.ports.out.CentralPortabilityPort;
import com.portability.domain.ports.out.CentralUnavailableException;
import com.portability.domain.ports.out.DiscountAgendaPort;
import com.portability.domain.ports.out.SignaturePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outbound HTTP adapter for the Central de Convênios.
 */
@Component
public class CentralClient implements DiscountAgendaPort, CentralPortabilityPort, CentralAuthorizationPort {

    private final RestClient restClient;
    private final SignaturePort signaturePort;
    private final ObjectMapper objectMapper;

    public CentralClient(
            @Value("${central.base-url}") String centralBaseUrl,
            SignaturePort signaturePort,
            ObjectMapper objectMapper
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = RestClient.builder()
                .baseUrl(centralBaseUrl)
                .requestFactory(requestFactory)
                .build();
        this.signaturePort = signaturePort;
        this.objectMapper = objectMapper;
    }

    @Override
    public String registerAuthorization(String beneficiaryId, String agreementId) {
        Map<String, String> request = new LinkedHashMap<>();
        request.put("beneficiarioId", beneficiaryId);
        request.put("convenioId", agreementId);

        String body;
        try {
            body = objectMapper.writeValueAsString(request);
        } catch (JacksonException exception) {
            throw new CentralUnavailableException("Could not build Central request", exception);
        }

        try {
            ResponseEntity<CentralAuthorizationResponse> response = restClient.post()
                    .uri("/autorizacoes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Signature", signaturePort.sign(body))
                    .body(body)
                    .retrieve()
                    .toEntity(CentralAuthorizationResponse.class);

            CentralAuthorizationResponse payload = response.getBody();
            if (response.getStatusCode() != HttpStatus.CREATED
                    || payload == null
                    || payload.autorizacaoId() == null
                    || payload.autorizacaoId().isBlank()) {
                throw new CentralUnavailableException("Unexpected response from Central");
            }
            return payload.autorizacaoId();
        } catch (RestClientException | JacksonException exception) {
            throw new CentralUnavailableException("Central indisponível", exception);
        }
    }

    @Override
    public List<DiscountInstallment> fetchAgenda(String beneficiaryId) {
        try {
            ResponseEntity<CentralInstallment[]> response = restClient.get()
                    .uri("/beneficiarios/{beneficiaryId}/agenda", beneficiaryId)
                    .retrieve()
                    .toEntity(CentralInstallment[].class);
            CentralInstallment[] items = response.getBody();
            if (response.getStatusCode() != HttpStatus.OK || items == null) {
                throw new CentralUnavailableException("Resposta inesperada da Central");
            }
            return Arrays.stream(items).map(CentralClient::toDomain).toList();
        } catch (RestClientException | JacksonException exception) {
            throw new CentralUnavailableException("Central indisponível", exception);
        }
    }

    private static DiscountInstallment toDomain(CentralInstallment item) {
        if (item == null || isBlank(item.parcelaId()) || isBlank(item.convenioId())
                || item.valor() == null || isBlank(item.dataDesconto()) || isBlank(item.status())) {
            throw new CentralUnavailableException("Resposta inesperada da Central");
        }
        InstallmentStatus status = switch (item.status()) {
            case "ABERTA" -> InstallmentStatus.OPEN;
            case "PORTADA" -> InstallmentStatus.PORTED;
            case "CANCELADA" -> InstallmentStatus.CANCELLED;
            default -> throw new CentralUnavailableException("Resposta inesperada da Central");
        };
        try {
            return new DiscountInstallment(item.parcelaId(), item.convenioId(), item.valor(),
                    LocalDate.parse(item.dataDesconto()), status);
        } catch (DateTimeParseException exception) {
            throw new CentralUnavailableException("Resposta inesperada da Central", exception);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public String registerPortability(
            String beneficiaryId,
            List<String> installmentIds,
            String creditorAssignee,
            ReceivingAccount receivingAccount
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("beneficiarioId", beneficiaryId);
        payload.put("parcelas", installmentIds);
        payload.put("credorCessionario", creditorAssignee);
        Map<String, String> account = new LinkedHashMap<>();
        account.put("banco", receivingAccount.bank());
        account.put("agencia", receivingAccount.agency());
        account.put("conta", receivingAccount.account());
        payload.put("contaRecebimento", account);

        try {
            String canonicalBody = objectMapper.writeValueAsString(payload);
            String signature = signaturePort.sign(canonicalBody);
            payload.put("assinatura", signature);
            String body = objectMapper.writeValueAsString(payload);

            ResponseEntity<CentralOperationResponse> response = restClient.post()
                    .uri("/operacoes-portabilidade")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Signature", signature)
                    .body(body)
                    .retrieve()
                    .toEntity(CentralOperationResponse.class);

            CentralOperationResponse result = response.getBody();
            if (response.getStatusCode() != HttpStatus.ACCEPTED
                    || result == null || isBlank(result.protocolo())) {
                throw new CentralUnavailableException("Resposta inesperada da Central");
            }
            return result.protocolo();
        } catch (RestClientException | JacksonException exception) {
            throw new CentralUnavailableException("Central indisponível", exception);
        }
    }

    @Override
    public CentralOperationStatus consultStatus(String protocol) {
        // TODO: GET {centralBaseUrl}/operacoes-portabilidade/{protocol} -> status / Retry-After (F04)
        throw new UnsupportedOperationException("TODO: consult Central status");
    }

    private record CentralAuthorizationResponse(String autorizacaoId) {
    }

    private record CentralOperationResponse(String protocolo) {
    }

    private record CentralInstallment(String parcelaId, String convenioId, BigDecimal valor,
                                      String dataDesconto, String status) {
    }
}
